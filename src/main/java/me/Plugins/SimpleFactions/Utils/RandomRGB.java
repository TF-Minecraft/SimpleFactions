package me.Plugins.SimpleFactions.Utils;

import java.util.Random;

import me.Plugins.SimpleFactions.Guild.Guild;
import me.Plugins.SimpleFactions.Managers.FactionManager;
import me.Plugins.SimpleFactions.Objects.Faction;

public class RandomRGB {
    private static final Random random = new Random();

    public static String random() {
        String rgb;
        do {
            float hue = random.nextFloat(); // 0.0 to 1.0
            float saturation = 0.6f + random.nextFloat() * 0.2f; // 0.6 to 0.8
            float lightness = 0.5f + random.nextFloat() * 0.15f; // 0.5 to 0.65

            rgb = hslToRGB(hue, saturation, lightness);
        } while (FactionManager.getByRGB(rgb) != null);

        return rgb;
    }
    
    public static String similarButDistinct(String rgb) {
        String[] parts = rgb.split(",");
        int r = Integer.parseInt(parts[0]);
        int g = Integer.parseInt(parts[1]);
        int b = Integer.parseInt(parts[2]);

        // Convert RGB to HSL
        float[] hsl = rgbToHSL(r, g, b);
        float h = hsl[0];
        float s = hsl[1];
        float l = hsl[2];

        // Apply small changes to hue, saturation, and lightness
        h += (random.nextFloat() - 0.5f) * 0.2f; // §0.1 shift
        s += (random.nextFloat() - 0.5f) * 0.1f; // §0.05
        l += (random.nextFloat() - 0.5f) * 0.1f; // §0.05

        // Clamp values
        h = (h + 1f) % 1f;
        s = Math.min(1f, Math.max(0f, s));
        l = Math.min(1f, Math.max(0f, l));

        // Convert back to RGB
        return hslToRGB(h, s, l);
    }

    // Converts HSL to RGB and returns a string like "r,g,b"
    private static String hslToRGB(float h, float s, float l) {
        float r, g, b;

        if (s == 0f) {
            r = g = b = l; // Achromatic
        } else {
            float q = l < 0.5 ? (l * (1 + s)) : (l + s - l * s);
            float p = 2 * l - q;
            r = hueToRGB(p, q, h + 1f / 3f);
            g = hueToRGB(p, q, h);
            b = hueToRGB(p, q, h - 1f / 3f);
        }

        return ((int)(r * 255)) + "," + ((int)(g * 255)) + "," + ((int)(b * 255));
    }
    
    private static float[] rgbToHSL(int r, int g, int b) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h, s, l;

        l = (max + min) / 2f;

        if (max == min) {
            h = s = 0f; // achromatic
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);

            if (max == rf) {
                h = (gf - bf) / d + (gf < bf ? 6f : 0f);
            } else if (max == gf) {
                h = (bf - rf) / d + 2f;
            } else {
                h = (rf - gf) / d + 4f;
            }
            h /= 6f;
        }

        return new float[] { h, s, l };
    }


    private static float hueToRGB(float p, float q, float t) {
        if (t < 0f) t += 1f;
        if (t > 1f) t -= 1f;
        if (t < 1f / 6f) return p + (q - p) * 6f * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f;
        return p;
    }

    public static boolean isFree(String rgb) {
        for(Faction f : FactionManager.factions) {
            if(f.getRGB().equalsIgnoreCase(rgb)) return false;
            for(Guild g : f.getGuildHandler().getGuilds()) {
                if(g.getRGB().equalsIgnoreCase(rgb)) return false;
            }
        }
        return true;
    }

}

