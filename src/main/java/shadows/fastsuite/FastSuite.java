package shadows.fastsuite;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import shadows.placebo.config.Configuration;

@Mod(FastSuite.MODID)
public class FastSuite {

	public static final String MODID = "fastsuite";
	public static final Logger LOG = LogManager.getLogger(MODID);

	public static int cacheSize = 100;

	public FastSuite() {
		Configuration cfg = new Configuration(MODID);
		cacheSize = cfg.getInt("Cache Size", "general", 100, 1, 100000, "The amount of recipes that will be cached by FastSuite.  This means that a recipe will not be pushed to the front of the list if it is within the first <n> recipes.");
		if (cfg.hasChanged()) cfg.save();
	}
}
