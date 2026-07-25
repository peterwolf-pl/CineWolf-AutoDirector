package pl.peterwolf.cinewolf.montage.event.detector;

import pl.peterwolf.cinewolf.montage.event.ReplayEventType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic block-id heuristics that specialize generic placement/destruction into
 * tree cutting, farming, and mining when the evidence is clear enough.
 */
final class BlockActivityClassifier {
    private static final Set<String> FARM_BLOCKS = Set.of(
            "farmland", "wheat", "carrots", "potatoes", "beetroots",
            "melon", "melon_stem", "attached_melon_stem",
            "pumpkin", "pumpkin_stem", "attached_pumpkin_stem",
            "nether_wart", "cocoa", "sweet_berry_bush", "sugar_cane", "cactus",
            "kelp", "kelp_plant", "bamboo", "bamboo_sapling",
            "cave_vines", "cave_vines_plant",
            "torchflower", "torchflower_crop", "pitcher_crop", "pitcher_plant",
            "chorus_flower", "chorus_plant", "crimson_fungus", "warped_fungus",
            "brown_mushroom", "red_mushroom", "nether_wart_block", "warped_wart_block");

    private static final Set<String> MINING_BLOCKS = Set.of(
            "stone", "cobblestone", "mossy_cobblestone", "smooth_stone",
            "granite", "diorite", "andesite", "deepslate", "cobbled_deepslate",
            "tuff", "calcite", "dripstone_block", "pointed_dripstone",
            "sandstone", "red_sandstone", "basalt", "smooth_basalt", "blackstone",
            "netherrack", "end_stone", "magma_block", "obsidian", "crying_obsidian",
            "amethyst_block", "budding_amethyst", "ice", "packed_ice", "blue_ice",
            "prismarine", "dark_prismarine", "sea_lantern",
            "ancient_debris", "nether_gold_ore", "gilded_blackstone",
            "gravel", "clay", "terracotta", "white_terracotta", "orange_terracotta",
            "magenta_terracotta", "light_blue_terracotta", "yellow_terracotta",
            "lime_terracotta", "pink_terracotta", "gray_terracotta",
            "light_gray_terracotta", "cyan_terracotta", "purple_terracotta",
            "blue_terracotta", "brown_terracotta", "green_terracotta",
            "red_terracotta", "black_terracotta");

    private BlockActivityClassifier() {
    }

    static Optional<ReplayEventType> specialize(ReplayEventType genericType, Collection<String> blockTypes) {
        if (blockTypes == null || blockTypes.isEmpty()) return Optional.empty();
        EnumMap<Category, Integer> votes = new EnumMap<>(Category.class);
        int total = 0;
        for (String blockType : blockTypes) {
            Category category = classify(genericType, blockType);
            if (category == Category.GENERIC) continue;
            votes.merge(category, 1, Integer::sum);
            total++;
        }
        if (total == 0) return Optional.empty();
        Map.Entry<Category, Integer> best = null;
        for (Map.Entry<Category, Integer> entry : votes.entrySet()) {
            if (best == null || entry.getValue() > best.getValue()
                    || (entry.getValue().equals(best.getValue())
                    && entry.getKey().ordinal() < best.getKey().ordinal())) {
                best = entry;
            }
        }
        if (best == null || best.getValue() * 2 < blockTypes.size()) return Optional.empty();
        return Optional.of(best.getKey().eventType);
    }

    static Category classify(ReplayEventType genericType, String blockType) {
        String id = normalize(blockType);
        if (id.isEmpty()) return Category.GENERIC;
        if (genericType == ReplayEventType.BLOCK_DESTRUCTION) {
            if (isTreeLog(id)) return Category.TREE;
            if (isFarmBlock(id)) return Category.FARM;
            if (isMiningBlock(id)) return Category.MINE;
            return Category.GENERIC;
        }
        if (genericType == ReplayEventType.BLOCK_PLACEMENT) {
            if (isFarmBlock(id) || "farmland".equals(id)) return Category.FARM;
            return Category.GENERIC;
        }
        return Category.GENERIC;
    }

    static String normalize(String blockType) {
        if (blockType == null) return "";
        String value = blockType.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) value = value.substring(colon + 1);
        return value;
    }

    private static boolean isTreeLog(String id) {
        if (id.startsWith("stripped_")) return true;
        if (id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae")) {
            return true;
        }
        return "mangrove_roots".equals(id) || "muddy_mangrove_roots".equals(id);
    }

    private static boolean isFarmBlock(String id) {
        if (FARM_BLOCKS.contains(id)) return true;
        return id.endsWith("_sapling") && !id.contains("azalea");
    }

    private static boolean isMiningBlock(String id) {
        if (MINING_BLOCKS.contains(id)) return true;
        if (id.endsWith("_ore") || id.startsWith("raw_") || id.endsWith("_raw_ore")) return true;
        if (id.contains("deepslate") && (id.contains("ore") || id.startsWith("polished_")
                || id.endsWith("_bricks") || id.endsWith("_tiles"))) {
            return true;
        }
        return id.endsWith("_concrete") || id.endsWith("_concrete_powder");
    }

    enum Category {
        TREE(ReplayEventType.TREE_CUTTING),
        FARM(ReplayEventType.FARMING),
        MINE(ReplayEventType.MINING),
        GENERIC(null);

        private final ReplayEventType eventType;

        Category(ReplayEventType eventType) {
            this.eventType = eventType;
        }
    }
}
