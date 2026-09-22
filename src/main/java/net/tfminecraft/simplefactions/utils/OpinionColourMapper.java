package net.tfminecraft.simplefactions.utils;

public class OpinionColourMapper {

    public static String getOpinionColor(int opinion) {
        // Clamp the opinion between -80 and +80
        opinion = Math.max(-80, Math.min(80, opinion));

        // Define RGB for -80 (dark red), 0 (muted yellow), +80 (light grayish green)
        int[] negColor = {128, 0, 0};
        int[] midColor = {212, 184, 47};
        int[] posColor = {93, 235, 54};

        float t;
        int[] resultColor = new int[3];

        if (opinion < 0) {
            t = (opinion + 80) / 80f;
            for (int i = 0; i < 3; i++) {
                resultColor[i] = (int) (negColor[i] * (1 - t) + midColor[i] * t);
            }
        } else {
            t = opinion / 80f;
            for (int i = 0; i < 3; i++) {
                resultColor[i] = (int) (midColor[i] * (1 - t) + posColor[i] * t);
            }
        }

        return String.format("#%02x%02x%02x", resultColor[0], resultColor[1], resultColor[2]);
    }
}
