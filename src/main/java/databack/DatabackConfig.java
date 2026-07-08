package databack;

import com.gtnewhorizon.gtnhlib.GTNHLib;
import com.gtnewhorizon.gtnhlib.config.Config;

@Config(modid = GTNHLib.MODID)
public class DatabackConfig {

    @Config.Comment("Do something")
    @Config.DefaultBoolean(false)
    public static boolean doSomething;

}
