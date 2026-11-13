package cn.kmbeast.core.voice;

import cn.kmbeast.core.voice.VoiceConfigHolder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Edge TTS 供应商
 * 
 * 使用微软 Edge 浏览器的 TTS 服务（免费，无需 API Key）
 * 通过 WebSocket 连接到微软的 TTS 服务
 * 
 * 特性：
 * - CompletableFuture 异步 API
 * - LRU 缓存避免重复合成
 * - 共享 OkHttpClient 连接池
 * - 配置（voice/speed/volume）运行时从 {@link VoiceConfigHolder} 读取，支持热更新
 * 
 * 支持的语音：
 * - zh-CN-XiaoxiaoNeural（女声，通用）
 * - zh-CN-YunxiNeural（男声，通用）
 * - zh-CN-YunjianNeural（男声，新闻）
 * - zh-CN-XiaoyiNeural（女声，童声）
 * - zh-CN-YunyangNeural（男声，新闻）
 */
@Slf4j
@Component
public class EdgeTTSProvider {

    private static final String EDGE_TTS_URL =
        "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";

    /** 建连必须携带的请求头（缺则微软直接拒连） */
    private static final String EDGE_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String EDGE_ORIGIN = "https://speech.platform.bing.com";

    /** 最大文本长度 */
    private static final int MAX_TEXT_LENGTH = 5000;

    /** LRU 缓存容量 */
    private static final int CACHE_CAPACITY = 100;

    @Autowired
    private VoiceConfigHolder voiceConfigHolder;

    /** 共享 OkHttpClient */
    private final OkHttpClient httpClient;

    /** LRU 缓存：key = text + "|" + voice, value = 音频数据 */
    private final ConcurrentHashMap<String, byte[]> cache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> cacheOrder = new ConcurrentLinkedDeque<>();

    public EdgeTTSProvider() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 合成语音（同步）
     * 
     * @param text 要合成的文本
     * @param voice 语音名称（如 zh-CN-XiaoxiaoNeural）
     * @return 识别结果
     */
    public VoiceResult<byte[]> synthesize(String text, String voice) {
        // 参数校验
        if (text == null || text.isEmpty()) {
            return VoiceResult.invalidInput("文本为空");
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            return VoiceResult.invalidInput("文本超过" + MAX_TEXT_LENGTH + "字符限制");
        }

        // 运行时从配置中心读取（支持热更新），未指定 voice 时回退到配置默认
        String resolvedVoice = (voice != null && !voice.isEmpty())
                ? voice : voiceConfigHolder.getTtsVoice();
        double resolvedRate = voiceConfigHolder.getTtsSpeed();
        int resolvedVolume = voiceConfigHolder.getTtsVolume();

        // 检查缓存
        String cacheKey = text + "|" + resolvedVoice;
        byte[] cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("[EdgeTTS] 缓存命中: key={}", cacheKey.substring(0, Math.min(50, cacheKey.length())));
            return VoiceResult.ok(cached);
        }

        try {
            // 使用共享 OkHttpClient 连接到 Edge TTS 服务
            EdgeTTSClient client = new EdgeTTSClient(httpClient, EDGE_TTS_URL, resolvedVoice, resolvedRate, resolvedVolume);
            byte[] audioData = client.synthesize(text);
            
            if (audioData.length == 0) {
                return VoiceResult.serviceUnavailable("合成失败，返回空数据");
            }

            // 存入缓存
            addToCache(cacheKey, audioData);
            
            return VoiceResult.ok(audioData);
        } catch (TimeoutException e) {
            log.error("[EdgeTTS] 合成超时", e);
            return VoiceResult.timeout();
        } catch (Exception e) {
            log.error("[EdgeTTS] 语音合成异常", e);
            return VoiceResult.serviceUnavailable("合成异常: " + e.getMessage());
        }
    }

    /**
     * 合成语音（异步）
     * 
     * @param text 要合成的文本
     * @param voice 语音名称
     * @return CompletableFuture
     */
    public CompletableFuture<VoiceResult<byte[]>> synthesizeAsync(String text, String voice) {
        // 2026-10-03 修复：原实现未传 Executor，会落到 ForkJoinPool.commonPool()。
        // 语音合成是阻塞式网络调用（数秒），并发上来会占满 commonPool 并拖垮其他 ForkJoin 业务。
        // 现指定语音合成专用线程池（有界队列 + CallerRuns 降级），见 VoiceSynthesisExecutor。
        return CompletableFuture.supplyAsync(
                () -> synthesize(text, voice),
                VoiceSynthesisExecutor.getInstance());
    }

    /**
     * 添加到 LRU 缓存
     */
    private void addToCache(String key, byte[] data) {
        // 清理超出容量的缓存
        while (cacheOrder.size() >= CACHE_CAPACITY) {
            String oldest = cacheOrder.pollFirst();
            if (oldest != null) {
                cache.remove(oldest);
            }
        }
        
        cache.put(key, data);
        cacheOrder.addLast(key);
    }

    /**
     * Edge TTS WebSocket 客户端
     */
    private static class EdgeTTSClient {
        private final OkHttpClient httpClient;
        private final String url;
        private final String voice;
        private final double rate;
        private final int volume;
        private final CompletableFuture<byte[]> future = new CompletableFuture<>();
        private volatile byte[] audioData = new byte[0];

        public EdgeTTSClient(OkHttpClient httpClient, String url, String voice, double rate, int volume) {
            this.httpClient = httpClient;
            this.url = url;
            this.voice = voice;
            this.rate = rate;
            this.volume = volume;
        }

        public byte[] synthesize(String text) throws Exception {
            Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", EDGE_USER_AGENT)
                .addHeader("Origin", EDGE_ORIGIN)
                .build();

            WebSocket ws = httpClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    // 发送配置消息
                    String configMsg = buildConfigMessage();
                    webSocket.send(configMsg);
                    
                    // 发送文本消息
                    String textMsg = buildTextMessage(text);
                    webSocket.send(textMsg);
                }

                @Override
                public void onMessage(WebSocket webSocket, String message) {
                    // 处理文本消息（配置响应等）
                    if (message.contains("Path:turn.end")) {
                        future.complete(audioData);
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    // 处理音频数据
                    byte[] data = bytes.toByteArray();
                    // 跳过头部信息（前 2 字节是长度）
                    if (data.length > 2) {
                        int headerLen = (data[0] << 8) | data[1];
                        if (data.length > 2 + headerLen) {
                            byte[] audio = new byte[data.length - 2 - headerLen];
                            System.arraycopy(data, 2 + headerLen, audio, 0, audio.length);
                            appendAudio(audio);
                        }
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    future.completeExceptionally(t);
                }
            });

            try {
                // 等待完成，最多30秒
                return future.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                ws.close(1000, "timeout");
                throw e;
            } catch (ExecutionException e) {
                throw new Exception(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        private String buildConfigMessage() {
            return String.format("""
                Content-Type:application/json; charset=utf-8\r\n
                Path:speech.config\r\n
                \r\n
                {
                    "context": {
                        "synthesis": {
                            "audio": {
                                "metadataoptions": {
                                    "sentenceBoundaryEnabled": "false",
                                    "wordBoundaryEnabled": "false"
                                },
                                "outputFormat": "audio-24khz-48kbitrate-mono-mp3"
                            }
                        }
                    }
                }
                """);
        }

        private String buildTextMessage(String text) {
            String escapedText = text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&quot;");
            
            return String.format("""
                Content-Type:application/ssml+xml\r\n
                Path:ssml\r\n
                \r\n
                <speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>
                    <voice name='%s'>
                        <prosody rate='%s' volume='%s'>
                            %s
                        </prosody>
                    </voice>
                </speak>
                """, voice, formatRate(rate), volume, escapedText);
        }

        private String formatRate(double rate) {
            if (rate == 1.0) return "+0%";
            if (rate > 1.0) return "+" + (int)((rate - 1.0) * 100) + "%";
            return "-" + (int)((1.0 - rate) * 100) + "%";
        }

        private synchronized void appendAudio(byte[] data) {
            byte[] newAudio = new byte[audioData.length + data.length];
            System.arraycopy(audioData, 0, newAudio, 0, audioData.length);
            System.arraycopy(data, 0, newAudio, audioData.length, data.length);
            audioData = newAudio;
        }
    }
}
