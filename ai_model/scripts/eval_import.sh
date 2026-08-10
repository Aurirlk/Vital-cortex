#!/bin/bash

# 问题1
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q1","question":"高血压患者日常生活中应该注意什么？","model_answer":"高血压患者日常生活中要遵循低盐、低脂、清淡饮食等健康饮食习惯，减少含高脂、高糖的饮食，也要避免暴饮暴食。在运动方面，患者应该选择适当的运动方式，运动量要注意适度，并时刻注意自己的身体状态，及时调整运动强度和方式。此外，高血压患者还要定期复查，及时调整治疗方案。","dimensions":{"accuracy":8,"professionalism":7,"safety":9,"completeness":6,"clarity":8},"overall_score":7.6,"comments":"饮食运动建议正确，但未引用学过的《中国高血压防治指南》，缺少量化建议（如钠摄入<5g/天）"}'

# 问题2
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q2","question":"最近总是失眠，有什么办法可以改善？","model_answer":"为了改善失眠症状，可以建立固定的作息规律，避免睡觉时使用电子设备，在睡前进行放松、减轻心理压力的活动，如阅读、冥想等。坚持锻炼，选择适合自己的放松方法，如热浴、冥想等，这些都有助于改善失眠症状。同时还需注意睡眠环境的营造和药物治疗的辅助。","dimensions":{"accuracy":7,"professionalism":7,"safety":8,"completeness":6,"clarity":7},"overall_score":7.0,"comments":"睡眠卫生建议正确，但缺少病因追问（病程？诱因？），结构不够系统化"}'

# 问题3
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q3","question":"感冒发烧了，需要吃抗生素吗？","model_answer":"不必要。感冒发烧是病毒性感染引起的，不需要服用抗生素，应多喝水、保持休息，并醋酸为主治疗。","dimensions":{"accuracy":9,"professionalism":6,"safety":8,"completeness":5,"clarity":5},"overall_score":6.6,"comments":"正确区分病毒/细菌感染，不乱开药——核心判断准确。但末尾 醋酸为主治疗 四字是无意义乱码，且未解释为什么不需抗生素"}'

# 问题4
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q4","question":"左胸口突然疼痛，呼吸加重，是什么原因？","model_answer":"左胸口疼痛，呼吸加重可能是因为肺部疾病引起的炎症，在肺循环时受到划伤或肺血管病变等导致。建议及时就医，进行进一步检查和治疗。","dimensions":{"accuracy":5,"professionalism":4,"safety":5,"completeness":4,"clarity":6},"overall_score":4.8,"comments":"胸痛+呼吸困难应优先排查心梗、肺栓塞、气胸等急症。模型只提了肺部原因，鉴别诊断不完整， 受到划伤 表述不专业。建议就医是对的，但漏掉了最危险的心源性可能"}'

# 问题5
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q5","question":"饭后经常反酸烧心，是怎么回事？","model_answer":"饭后经常反酸烧心可能是胃食管反流病导致的，应该注意饮食清淡，避免烟酒刺激，选择易消化的食物。적이거나,بيبجي,在这方面的话，可以尝试服用一些抗酸药和胃动力药，如铝碳酸镁、氢氧化铝等。如果症状持续不缓解，需要及时就医。","dimensions":{"accuracy":8,"professionalism":5,"safety":8,"completeness":7,"clarity":4},"overall_score":6.4,"comments":"GERD判断正确，OTC建议合理（铝碳酸镁/氢氧化铝）。但出现韩文/阿拉伯文乱码 '적이거나,بيبجي'，严重影响可读性和可信度"}'

# 问题6
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q6","question":"我有高血压和糖尿病，这两种药可以一起吃吗？","model_answer":"可以，噻吗洛尔滴眼液和吉非罗齐片都可以治疗高血压和糖尿病。","dimensions":{"accuracy":2,"professionalism":2,"safety":1,"completeness":2,"clarity":6},"overall_score":2.6,"comments":"严重幻觉！用户未说明具体药名，模型凭空编造 噻吗洛尔滴眼液 和 吉非罗齐片。噻吗洛尔是青光眼药非一线降压药，吉非罗齐是降脂药非降糖药。未追问具体药品、未给免责声明——存在安全隐患"}'

# 问题7
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q7","question":"布洛芬和阿莫西林能同时服用吗？","model_answer":"布洛芬和阿莫西林可以同时服用，但需要注意可能的药物相互作用。","dimensions":{"accuracy":6,"professionalism":5,"safety":7,"completeness":4,"clarity":7},"overall_score":5.8,"comments":"核心判断基本正确（NSAID+抗生素无直接禁忌），但过于简略。未解释两者机制差异、未提醒布洛芬可能掩盖感染发热、未说明服用间隔建议"}'

# 问题8
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q8","question":"空腹血糖6.8 mmol/L，算是糖尿病前期吗？需要怎么控制？","model_answer":"空腹血糖6.8 mmol/L属于糖尿病前期。建议注意饮食、加强运动、积极减重，药物治疗是发病年龄超过40岁者、有糖尿病家族史的高危人群可以选择药物和胰岛素治疗。","dimensions":{"accuracy":8,"professionalism":7,"safety":7,"completeness":7,"clarity":8},"overall_score":7.4,"comments":"正确判断糖尿病前期（6.1-7.0为IFG），数值解读准确。饮食运动减重建议到位。但药物治疗表述偏激进——糖尿病前期首选生活方式干预，药物是二线选择"}'

# 问题9
curl -s -X POST http://localhost:42706/eval/score \
  -H "Content-Type: application/json" \
  -d '{"question_id":"Q9","question":"孕妇感冒了怎么办？能吃药吗？","model_answer":"孕妇感冒后应注意休息、多饮水、保持室内空气流通，并根据病情适当采取药物治疗。但由于孕妇用药要慎之又慎，因此使用药物前应先咨询医生，避免对胎儿造成影响。","dimensions":{"accuracy":8,"professionalism":8,"safety":9,"completeness":6,"clarity":8},"overall_score":7.8,"comments":"安全意识出色——强调咨询医生+避免胎儿影响。建议正确（休息/饮水/通风）。可提升：未说明哪些相对安全的药（如对乙酰氨基酚），未提醒发热需重点关注"}'

echo ""
echo "=== 所有打分已提交 ==="
echo ""

# 生成评测报告
curl http://localhost:42706/eval/summary
