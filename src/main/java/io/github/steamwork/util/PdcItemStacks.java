package io.github.steamwork.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Serializes a single ItemStack into a PDC byte array.
 *
 * <p>The Bukkit object stream keeps the full ItemStack state, including PDC and
 * component data. Serialization failures intentionally return null so callers can
 * fall back without preventing block loading.</p>
 */
@SuppressWarnings("deprecation")
public final class PdcItemStacks {

    private PdcItemStacks() {
        throw new AssertionError("Utility class");
    }

    public static final PersistentDataType<byte[], byte[]> TYPE = PersistentDataType.BYTE_ARRAY;

    public static byte @Nullable [] toBytes(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteOut)) {
            out.writeObject(item);
            return byteOut.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public static @Nullable ItemStack fromBytes(byte @Nullable [] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(bytes))) {
            Object obj = in.readObject();
            return obj instanceof ItemStack is ? is : null;
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
}
