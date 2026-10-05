import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import java.io.PrintWriter;

public class RunAll {
    public static void main(String[] args) {
        LauncherDiscoveryRequest req = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectPackage("cn.kmbeast")).build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener l = new SummaryGeneratingListener();
        launcher.execute(req, l);
        l.getSummary().printTo(new PrintWriter(System.out));
        l.getSummary().printFailuresTo(new PrintWriter(System.out));
    }
}
