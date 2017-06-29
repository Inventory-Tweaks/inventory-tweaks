package invtweaks.forge.asm;

import com.google.common.collect.Lists;
import invtweaks.forge.asm.compatibility.CompatibilityConfigLoader;
import invtweaks.forge.asm.compatibility.ContainerInfo;
import invtweaks.forge.asm.compatibility.MethodInfo;
import net.minecraft.launchwrapper.IClassTransformer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.Consumer;

import org.apache.logging.log4j.Logger;

public class ContainerTransformer implements IClassTransformer {
    private static final String VALID_INVENTORY_METHOD = "invtweaks$validInventory";
    private static final String VALID_CHEST_METHOD = "invtweaks$validChest";
    private static final String LARGE_CHEST_METHOD = "invtweaks$largeChest";
    private static final String SHOW_BUTTONS_METHOD = "invtweaks$showButtons";
    private static final String ROW_SIZE_METHOD = "invtweaks$rowSize";
    private static final String SLOT_MAP_METHOD = "invtweaks$slotMap";
    private static final String CONTAINER_CLASS_INTERNAL = "net/minecraft/inventory/Container";
    private static final String SLOT_MAPS_VANILLA_CLASS = "invtweaks/container/VanillaSlotMaps";
    private static final String ANNOTATION_CHEST_CONTAINER = "Linvtweaks/api/container/ChestContainer;";
    private static final String ANNOTATION_CHEST_CONTAINER_ROW_CALLBACK = "Linvtweaks/api/container/ChestContainer$RowSizeCallback;";
    private static final String ANNOTATION_CHEST_CONTAINER_LARGE_CALLBACK = "Linvtweaks/api/container/ChestContainer$IsLargeCallback;";
    private static final String ANNOTATION_INVENTORY_CONTAINER = "Linvtweaks/api/container/InventoryContainer;";
    private static final String ANNOTATION_IGNORE_CONTAINER = "Linvtweaks/api/container/IgnoreContainer;";
    private static final String ANNOTATION_CONTAINER_SECTION_CALLBACK = "Linvtweaks/api/container/ContainerSectionCallback;";

    private static List<String> uninterestingPackages = Lists.newArrayList(
            "net.minecraft.",
            "net.minecraftforge.",
            "joptsimple.",
            "com.mojang.",
            "com.google.gson.",
            "io.netty.",
            "oshi.",
            "com.sun.jna.",
            "com.ibm.icu.",
            "org.slf4j.",
            "javassist.",
            "gnu.trove.",
            "paulscode.sound.",
            "com.jcraft.jogg.",
            "com.jcraft.jorbis.",
            "it.unimi.dsi.fastutil."
    );

    private static final Logger logger = org.apache.logging.log4j.LogManager.getLogger();
    
    @NotNull
    private static Map<String, ContainerInfo> standardClasses = new HashMap<>();
    @NotNull
    private static Map<String, ContainerInfo> configClasses = new HashMap<>();

    public ContainerTransformer() {
        lateInit();
    }
    
    /**
     * Alter class to contain information contained by ContainerInfo
     *
     * @param clazz Class to alter
     * @param info  Information used to alter class
     */
    private static void transformContainer(@NotNull ClassNode clazz, @NotNull ContainerInfo info) {
        ASMHelper.generateBooleanMethodConst(clazz, SHOW_BUTTONS_METHOD, info.showButtons);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_INVENTORY_METHOD, info.validInventory);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, info.validChest);

        if(info.largeChestMethod != null) {
            if(info.largeChestMethod.isStatic) {
                ASMHelper.generateForwardingToStaticMethod(clazz, LARGE_CHEST_METHOD, info.largeChestMethod.methodName,
                        info.largeChestMethod.methodType.getReturnType(),
                        info.largeChestMethod.methodClass,
                        info.largeChestMethod.methodType.getArgumentTypes()[0]);
            } else {
                ASMHelper.generateSelfForwardingMethod(clazz, LARGE_CHEST_METHOD, info.largeChestMethod.methodName,
                        info.largeChestMethod.methodType.getReturnType());
            }
        } else {
            ASMHelper.generateBooleanMethodConst(clazz, LARGE_CHEST_METHOD, info.largeChest);
        }

        if(info.rowSizeMethod != null) {
            if(info.rowSizeMethod.isStatic) {
                ASMHelper.generateForwardingToStaticMethod(clazz, ROW_SIZE_METHOD, info.rowSizeMethod.methodName,
                        info.rowSizeMethod.methodType.getReturnType(),
                        info.rowSizeMethod.methodClass,
                        info.rowSizeMethod.methodType.getArgumentTypes()[0]);
            } else {
                ASMHelper.generateSelfForwardingMethod(clazz, ROW_SIZE_METHOD, info.rowSizeMethod.methodName,
                        info.rowSizeMethod.methodType.getReturnType());
            }
        } else {
            ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, info.rowSize);
        }

        if(info.slotMapMethod.isStatic) {
            ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, info.slotMapMethod.methodName,
                    info.slotMapMethod.methodType.getReturnType(),
                    info.slotMapMethod.methodClass,
                    info.slotMapMethod.methodType.getArgumentTypes()[0]);
        } else {
            ASMHelper.generateSelfForwardingMethod(clazz, SLOT_MAP_METHOD, info.slotMapMethod.methodName,
                    info.slotMapMethod.methodType.getReturnType());
        }
    }

    /**
     * Alter class to contain default implementations of added methods.
     *
     * @param clazz Class to alter
     */
    private static void transformBaseContainer(@NotNull ClassNode clazz) {
        ASMHelper.generateBooleanMethodConst(clazz, SHOW_BUTTONS_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_INVENTORY_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, LARGE_CHEST_METHOD, false);
        ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, (short) 9);
        ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, "unknownContainerSlots",
                Type.getObjectType("java/util/Map"),
                Type.getObjectType(SLOT_MAPS_VANILLA_CLASS),
                Type.getObjectType(CONTAINER_CLASS_INTERNAL));
    }

    private static void transformCreativeContainer(@NotNull ClassNode clazz) {
        ASMHelper.generateForwardingToStaticMethod(clazz, SHOW_BUTTONS_METHOD, "containerCreativeIsInventory",
                Type.BOOLEAN_TYPE, Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
        ASMHelper.generateForwardingToStaticMethod(clazz, VALID_INVENTORY_METHOD, "containerCreativeIsInventory",
                Type.BOOLEAN_TYPE, Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, LARGE_CHEST_METHOD, false);
        ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, (short) 9);
        ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, "containerCreativeSlots",
                Type.getObjectType("java/util/Map"),
                Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
    }

    private static void transformHorseInventoryContainer(@NotNull ClassNode clazz) {
        ASMHelper.generateForwardingToStaticMethod(clazz, SHOW_BUTTONS_METHOD, "containerHorseIsInventory",
                Type.BOOLEAN_TYPE, Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
        ASMHelper.generateForwardingToStaticMethod(clazz, VALID_INVENTORY_METHOD, "containerHorseIsInventory",
                Type.BOOLEAN_TYPE, Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, true);
        ASMHelper.generateBooleanMethodConst(clazz, LARGE_CHEST_METHOD, false);
        ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, (short) 5);
        ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, "containerHorseSlots",
                Type.getObjectType("java/util/Map"),
                Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
    }

    private static void transformInvTweaksObfuscation(@NotNull ClassNode clazz) {
        @NotNull Type containertype = Type.getObjectType(CONTAINER_CLASS_INTERNAL);
        for(@NotNull MethodNode method : clazz.methods) {
            if("isValidChest".equals(method.name)) {
                ASMHelper.replaceSelfForwardingMethod(method, VALID_CHEST_METHOD, containertype);
            } else if("isValidInventory".equals(method.name)) {
                ASMHelper.replaceSelfForwardingMethod(method, VALID_INVENTORY_METHOD, containertype);
            } else if("showButtons".equals(method.name)) {
                ASMHelper.replaceSelfForwardingMethod(method, SHOW_BUTTONS_METHOD, containertype);
            } else if("getSpecialChestRowSize".equals(method.name)) {
                ASMHelper.replaceSelfForwardingMethod(method, ROW_SIZE_METHOD, containertype);
            } else if("getContainerSlotMap".equals(method.name)) {
                ASMHelper.replaceSelfForwardingMethod(method, SLOT_MAP_METHOD, containertype);
            } else if("isLargeChest".equals(method.name)) {
                ASMHelper.replaceSelfForwardingMethod(method, LARGE_CHEST_METHOD, containertype);
            }
        }
    }

    private static void transformTextField(@NotNull ClassNode clazz) {
        for(@NotNull MethodNode method : clazz.methods) {
            if(("func_146195_b".equals(method.name) || "setFocused".equals(method.name))&& "(Z)V".equals(method.desc)) {
                InsnList code = method.instructions;
                @Nullable AbstractInsnNode returnNode = null;
                for(ListIterator<AbstractInsnNode> iterator = code.iterator(); iterator.hasNext(); ) {
                    AbstractInsnNode insn = iterator.next();

                    if(insn.getOpcode() == Opcodes.RETURN) {
                        returnNode = insn;
                        break;
                    }
                }

                if(returnNode != null) {
                    // Insert a call to helper method to disable sorting while a text field is focused
                    code.insertBefore(returnNode, new VarInsnNode(Opcodes.ILOAD, 1));
                    code.insertBefore(returnNode,
                            new MethodInsnNode(Opcodes.INVOKESTATIC, "invtweaks/forge/InvTweaksMod",
                                    "setTextboxModeStatic", "(Z)V", false));
                    logger.info("InvTweaks: successfully transformed setFocused/func_146195_b");
                } else {
                    logger.fatal("InvTweaks: unable to find return in setFocused/func_146195_b");
                }
            }
        }
    }

    @NotNull
    public static MethodInfo getVanillaSlotMapInfo(String name) {
        return getSlotMapInfo(Type.getObjectType(SLOT_MAPS_VANILLA_CLASS), name, true);
    }

    @NotNull
    private static MethodInfo getSlotMapInfo(Type mClass, String name, boolean isStatic) {
        return new MethodInfo(
                Type.getMethodType(Type.getObjectType("java/util/Map"), Type.getObjectType(CONTAINER_CLASS_INTERNAL)), mClass,
                name, isStatic);
    }

    // This needs to have access to the FML remapper so it needs to run when we know it's been set up correctly.
    private static void lateInit() {
        // Standard non-chest type
        standardClasses.put("net.minecraft.inventory.ContainerPlayer",
                new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerPlayerSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerMerchant", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerRepair",
                new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerRepairSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerHopper", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerBeacon", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerBrewingStand",
                new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerBrewingSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerWorkbench",
                new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerWorkbenchSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerEnchantment",
                new ContainerInfo(false, true, false, getVanillaSlotMapInfo("containerEnchantmentSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerFurnace",
                new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerFurnaceSlots")));

        // Chest-type
        standardClasses.put("net.minecraft.inventory.ContainerDispenser",
                new ContainerInfo(true, false, true, (short) 3,
                        getVanillaSlotMapInfo("containerChestDispenserSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerChest", new ContainerInfo(true, false, true,
                getVanillaSlotMapInfo(
                        "containerChestDispenserSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerShulkerBox", new ContainerInfo(true, false, true,
                getVanillaSlotMapInfo(
                        "containerChestDispenserSlots")));

        try {
            configClasses = CompatibilityConfigLoader.load("config/InvTweaksCompatibility.xml");
        } catch(FileNotFoundException ex) {
            configClasses = new HashMap<>();
        } catch(Exception ex) {
            configClasses = new HashMap<>();
            ex.printStackTrace();
        }
    }

    private static MethodNode findAnnotatedMethod(@NotNull ClassNode cn, @NotNull String annotationDesc) {
        for(@NotNull MethodNode method : cn.methods) {
            if(method.visibleAnnotations != null) {
                for(@NotNull AnnotationNode methodAnnotation : method.visibleAnnotations) {
                    if(annotationDesc.equals(methodAnnotation.desc)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static byte[] doTransform(byte[] bytes, Consumer<ClassNode> transform) {
        @NotNull ClassReader cr = new ClassReader(bytes);
        @NotNull ClassNode cn = new ClassNode(Opcodes.ASM4);
        @NotNull ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cr.accept(cn, 0);

        transform.accept(cn);

        cn.accept(cw);
        return cw.toByteArray();
    }

    @Nullable
    @Override
    public byte[] transform(String name, String transformedName, @Nullable byte[] bytes) {
        // Sanity checking so it doesn't look like this mod caused crashes when things were missing.
        if(bytes == null || bytes.length == 0) {
            return bytes;
        }


        if("net.minecraft.client.gui.GuiTextField".equals(transformedName)) {
            return doTransform(bytes, ContainerTransformer::transformTextField);
        }

        if("net.minecraft.inventory.Container".equals(transformedName)) {
            return doTransform(bytes, ContainerTransformer::transformBaseContainer);
        }

        // TODO: Creative mode handling is really buggy for some reason.
        if("net.minecraft.client.gui.inventory.GuiContainerCreative$ContainerCreative".equals(transformedName)) {
            return doTransform(bytes, ContainerTransformer::transformCreativeContainer);
        }

        if("net.minecraft.inventory.ContainerHorseInventory".equals(transformedName)) {
            return doTransform(bytes, ContainerTransformer::transformHorseInventoryContainer);
        }

        if("invtweaks.InvTweaksObfuscation".equals(transformedName)) {
            return doTransform(bytes, ContainerTransformer::transformInvTweaksObfuscation);
        }

        // Transform classes with explicitly specified information
        final ContainerInfo standardInfo = standardClasses.get(transformedName);
        if(standardInfo != null) {
            return doTransform(bytes, cn -> transformContainer(cn, standardInfo));
        }

        final ContainerInfo configInfo = configClasses.get(transformedName);
        if(configInfo != null) {
            return doTransform(bytes, cn -> transformContainer(cn, configInfo));
        }

        // Skip any classes in 'uninteresting' (i.e., known non-Mod) packages
        for(String uninterestingPackage : uninterestingPackages) {
            if(transformedName.startsWith(uninterestingPackage)) {
                return bytes;
            }
        }

        @NotNull ClassReader cr = new ClassReader(bytes);
        @NotNull ClassNode cn = new ClassNode(Opcodes.ASM4);
        @NotNull ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cr.accept(cn, 0);

        if(cn.visibleAnnotations != null) {
            for(@Nullable AnnotationNode annotation : cn.visibleAnnotations) {
                if(annotation != null) {
                    @Nullable ContainerInfo apiInfo = null;

                    if(ANNOTATION_CHEST_CONTAINER.equals(annotation.desc)) {
                        short rowSize = 9;
                        boolean isLargeChest = false;
                        boolean showButtons = true;

                        if(annotation.values != null) {
                            for(int i = 0; i < annotation.values.size(); i += 2) {
                                @NotNull String valueName = (String) annotation.values.get(i);
                                Object value = annotation.values.get(i + 1);

                                switch(valueName) {
                                    case "rowSize":
                                        rowSize = (short) ((Integer) value).intValue();
                                        break;
                                    case "isLargeChest":
                                        isLargeChest = (Boolean) value;
                                        break;
                                    case "showButtons":
                                        showButtons = (Boolean) value;
                                        break;
                                }
                            }
                        }

                        apiInfo = new ContainerInfo(showButtons, false, true, isLargeChest, rowSize);

                        @Nullable MethodNode row_method = findAnnotatedMethod(cn, ANNOTATION_CHEST_CONTAINER_ROW_CALLBACK);

                        if(row_method != null) {
                            apiInfo.rowSizeMethod = new MethodInfo(Type.getMethodType(row_method.desc),
                                    Type.getObjectType(cn.name), row_method.name);
                        }

                        @Nullable MethodNode large_method = findAnnotatedMethod(cn, ANNOTATION_CHEST_CONTAINER_LARGE_CALLBACK);

                        if(large_method != null) {
                            apiInfo.largeChestMethod = new MethodInfo(Type.getMethodType(large_method.desc),
                                    Type.getObjectType(cn.name), large_method.name);
                        }
                    } else if(ANNOTATION_INVENTORY_CONTAINER.equals(annotation.desc)) {
                        boolean showOptions = true;

                        if(annotation.values != null) {
                            for(int i = 0; i < annotation.values.size(); i += 2) {
                                @NotNull String valueName = (String) annotation.values.get(i);
                                Object value = annotation.values.get(i + 1);

                                if("showOptions".equals(valueName)) {
                                    showOptions = (Boolean) value;
                                }
                            }
                        }

                        apiInfo = new ContainerInfo(showOptions, true, false);
                    } else if(ANNOTATION_IGNORE_CONTAINER.equals(annotation.desc)) {
                        // Annotation to restore default properties.

                        transformBaseContainer(cn);

                        cn.accept(cw);
                        return cw.toByteArray();
                    }

                    if(apiInfo != null) {
                        // Search methods to see if any have the ContainerSectionCallback attribute.
                        @Nullable MethodNode method = findAnnotatedMethod(cn, ANNOTATION_CONTAINER_SECTION_CALLBACK);

                        if(method != null) {
                            apiInfo.slotMapMethod = new MethodInfo(Type.getMethodType(method.desc),
                                    Type.getObjectType(cn.name), method.name);
                        }

                        transformContainer(cn, apiInfo);

                        cn.accept(cw);
                        return cw.toByteArray();
                    }
                }
            }
        }

        return bytes;
    }
}
