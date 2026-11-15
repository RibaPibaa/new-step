package com.multiverse.mod;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Hero suit items and the single place that decides "is this entity wearing the suit". */
public final class Heroes {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MultiverseMod.MOD_ID);

    public static final RegistryObject<Item> HOMELANDER_CHEST = ITEMS.register("homelander_chestplate",
            () -> new ArmorItem(SuitMaterial.HOMELANDER, EquipmentSlot.CHEST, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HOMELANDER_LEGS = ITEMS.register("homelander_leggings",
            () -> new ArmorItem(SuitMaterial.HOMELANDER, EquipmentSlot.LEGS, new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> HOMELANDER_BOOTS = ITEMS.register("homelander_boots",
            () -> new ArmorItem(SuitMaterial.HOMELANDER, EquipmentSlot.FEET, new Item.Properties().stacksTo(1)));

    private Heroes() {}

    /** True only when the full Homelander set is equipped. */
    public static boolean isWearingHomelander(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.CHEST).is(HOMELANDER_CHEST.get())
                && e.getItemBySlot(EquipmentSlot.LEGS).is(HOMELANDER_LEGS.get())
                && e.getItemBySlot(EquipmentSlot.FEET).is(HOMELANDER_BOOTS.get());
    }

    public enum SuitMaterial implements ArmorMaterial {
        HOMELANDER;

        @Override public int getDurabilityForSlot(EquipmentSlot slot) { return 0; }
        @Override public int getDefenseForSlot(EquipmentSlot slot) { return 3; }
        @Override public int getEnchantmentValue() { return 0; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_IRON; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return MultiverseMod.MOD_ID + ":homelander"; }
        @Override public float getToughness() { return 2.0F; }
        @Override public float getKnockbackResistance() { return 0.1F; }
    }
}
