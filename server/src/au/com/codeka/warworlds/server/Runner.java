package au.com.codeka.warworlds.server;

import org.eclipse.jetty.server.Server;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.codeka.warworlds.server.ctrl.NameGenerator;
import au.com.codeka.warworlds.server.ctrl.StarController;
import au.com.codeka.warworlds.server.ctrl.StatisticsController;
import au.com.codeka.warworlds.server.handlers.pages.HtmlPageHandler;
import au.com.codeka.warworlds.server.model.DesignManager;

public class Runner {
    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    public static void main(String[] args) throws Exception {
        String basePath = System.getProperty("au.com.codeka.warworlds.server.basePath");
        if (basePath == null) {
            basePath = HtmlPageHandler.class.getClassLoader().getResource("").getPath();
        }

        DesignManager.setup(basePath);
        NameGenerator.setup(basePath);

        if (args.length >= 2 && args[0].equals("cron")) {
            String extra = null;
            if (args.length >= 3) {
                extra = args[2];
            }
            cronMain(args[1], extra);
        } else {
            // kick off the event processor thread
            EventProcessor.i.ping();

            Server server = new Server(8080);
            server.setHandler(new RequestRouter());
            server.start();
            server.join();
        }
    }

    private static void cronMain(String method, String extra) {
        try {
            if (method.equals("update-ranks")) {
                new StatisticsController().updateRanks();
            } else if (method.equals("simulate-stars")) {
                int numHours = 6;
                if (extra != null) {
                    numHours = Integer.parseInt(extra);
                }
                new StarController().simulateAllStarsOlderThan(DateTime.now().minusHours(numHours));
            }
        } catch(Exception e) {
            log.error("Error running CRON", e);
        }
    }
}
