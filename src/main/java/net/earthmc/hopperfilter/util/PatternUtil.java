package net.earthmc.hopperfilter.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for handling patterns and conversions related to Components and Registry keys.
 */
public class PatternUtil {

    /**
     * Serializes a Component to a plain text String.
     *
     * @param component the Component to serialize
     * @return the serialized plain text String, or null if the component is null
     */
    public static @Nullable String serialiseComponent(final Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * Retrieves a Keyed object from a string using the specified registry.
     *
     * @param string   the string representation of the key
     * @param registry the registry to retrieve the Keyed object from
     * @param <T>      the type of the Keyed object
     * @return the Keyed object, or null if not found
     */
    public static <T extends Keyed> @Nullable T getKeyedFromString(String string, Registry<T> registry) {
        final NamespacedKey key = NamespacedKey.minecraft(string);

        return registry.get(key);
    }

    /**
     * Parses an integer from a string.
     *
     * @param string the string to parse
     * @return the parsed integer, or null if the string is not a valid integer
     */
    public static @Nullable Integer getIntegerFromString(String string) {
        try {
            return Integer.valueOf(string);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Splits a string into a pair of string and integer based on an underscore separator.
     *
     * @param string the string to split
     * @return a Pair containing the string part and the integer part, or null if no integer part is found
     */
    public static Pair<String, Integer> getStringIntegerPairFromString(String string) {
        final String[] split = string.split("_");
        if (split.length == 1) return Pair.of(split[0], null);

        final Integer integer = getIntegerFromString(split[split.length - 1]);
        if (integer == null) {
            return Pair.of(String.join("_", split), null);
        } else {
            final List<String> list = new ArrayList<>(List.of(split));
            list.remove(list.size() - 1);
            return Pair.of(String.join("_", list), integer);
        }
    }
}
