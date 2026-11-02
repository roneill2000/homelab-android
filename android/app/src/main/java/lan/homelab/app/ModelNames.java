package lan.homelab.app;

import java.util.Locale;

// Friendly names for the model ids /api/widget reports (the dashboard's PC_MODEL_NAMES), for the widget and the AI
// engine tile. Unknown ids are shown as they are.
final class ModelNames {
    private ModelNames() {}

    static String label(String id) {
        if (id == null || id.isEmpty()) return "";
        switch (id) {
            case "qwen3.8-27b": return "Qwen3.8 27B";
            case "qwen3.8-27b-uncensored": return "Qwen3.8 27B Uncensored";
            case "gemma-4-26b-a4b": return "Gemma 4 26B MoE";
            case "qwen3.6-35b-moe": return "Qwen3.6 35B MoE";
            case "qwen3.8-flash-next-iq3_xxs": return "Strata · Qwen3.8 Flash IQ3_XXS";
            case "qwen3.8-flash-next-coder-iq1_m": return "Strata · Qwen3.8 Coder IQ1_M";
            default: break;
        }
        // Strata: swift-1.5-iq3_xxs -> "Strata · Swift 1.5 IQ3_XXS"
        if (id.startsWith("swift-")) {
            String[] parts = id.substring(6).split("-", 2);
            return "Strata · Swift " + parts[0] + (parts.length > 1 ? " " + parts[1].toUpperCase(Locale.ROOT) : "");
        }
        return id;
    }

    static boolean isStrata(String id) { return id != null && (id.startsWith("swift-") || id.startsWith("qwen3.8-flash-next")); }
}
