package io;

import common.io.assets.UpdateCheck.UpdateJson;
import online.tests.Check;

/** Regression coverage for offline/partial updateInfo.json startup handling. */
public final class BCJSONTests {
    public static void run() {
        Check.equal(0, BCJSON.announcements(null).length,
                "missing updateInfo must expose no announcements");
        Check.equal(0, BCJSON.getLatestJars(null).length,
                "missing updateInfo must expose no jar updates");

        UpdateJson partial = new UpdateJson();
        Check.equal(0, BCJSON.announcements(partial).length,
                "missing pc_announcement field must be tolerated");
        Check.equal(0, BCJSON.getLatestJars(partial).length,
                "missing pc_update field must be tolerated");
    }
}
