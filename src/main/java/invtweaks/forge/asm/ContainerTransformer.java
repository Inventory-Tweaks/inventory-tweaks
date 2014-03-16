package invtweaks.forge.asm;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import invtweaks.forge.asm.compatibility.CompatibilityConfigLoader;
import invtweaks.forge.asm.compatibility.ContainerInfo;
import invtweaks.forge.asm.compatibility.MethodInfo;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


public class ContainerTransformer implements IClassTransformer {
    public static final String VALID_INVENTORY_METHOD = "invtweaks$validInventory";
    public static final String VALID_CHEST_METHOD = "invtweaks$validChest";
    public static final String LARGE_CHEST_METHOD = "invtweaks$largeChest";
    public static final String STANDARD_INVENTORY_METHOD = "invtweaks$standardInventory";
    public static final String ROW_SIZE_METHOD = "invtweaks$rowSize";
    public static final String SLOT_MAP_METHOD = "invtweaks$slotMap";
    public static final String CONTAINER_CLASS_INTERNAL = "net/minecraft/inventory/Container";
    public static final String SLOT_MAPS_VANILLA_CLASS = "invtweaks/containers/VanillaSlotMaps";
    public static final String SLOT_MAPS_MODCOMPAT_CLASS = "invtweaks/containers/CompatibilitySlotMaps";
    public static final String ANNOTATION_CHEST_CONTAINER = "Linvtweaks/api/container/ChestContainer;";
    public static final String ANNOTATION_CHEST_CONTAINER_ROW_CALLBACK = "Linvtweaks/api/container/ChestContainer$RowSizeCallback;";
    public static final String ANNOTATION_INVENTORY_CONTAINER = "Linvtweaks/api/container/InventoryContainer;";
    public static final String ANNOTATION_CONTAINER_SECTION_CALLBACK = "Linvtweaks/api/container/ContainerSectionCallback;";

    private static Map<String, ContainerInfo> standardClasses = new HashMap<String, ContainerInfo>();
    private static Map<String, ContainerInfo> compatibilityClasses = new HashMap<String, ContainerInfo>();
    private static Map<String, ContainerInfo> configClasses = new HashMap<String, ContainerInfo>();
    private static String containerClassName;

    public ContainerTransformer() {
    }

    // This needs to have access to the FML remapper so it needs to run when we know it's been set up correctly.
    private void lateInit() {
        // TODO: ContainerCreative handling
        // Standard non-chest type
        standardClasses.put("net.minecraft.inventory.ContainerPlayer",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerPlayerSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerMerchant", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerRepair",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerPlayerSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerHopper", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerBeacon", new ContainerInfo(true, true, false));
        standardClasses.put("net.minecraft.inventory.ContainerBrewingStand",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerBrewingSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerWorkbench",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerWorkbenchSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerEnchantment",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerEnchantmentSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerFurnace",
                            new ContainerInfo(true, true, false, getVanillaSlotMapInfo("containerFurnaceSlots")));

        // Chest-type
        standardClasses.put("net.minecraft.inventory.ContainerDispenser",
                            new ContainerInfo(false, false, true, (short) 3,
                                              getVanillaSlotMapInfo("containerChestDispenserSlots")));
        standardClasses.put("net.minecraft.inventory.ContainerChest", new ContainerInfo(false, false, true,
                                                                                        getVanillaSlotMapInfo(
                                                                                                "containerChestDispenserSlots")));

        // Mod compatibility
        // Iron Chests
        ContainerInfo ironChestsInfo = new ContainerInfo(false, false, true, true);
        compatibilityClasses.put("cpw.mods.ironchest.ContainerIronChest", ironChestsInfo);
        // Equivalent Exchange 3
        compatibilityClasses.put("com.pahimar.ee3.inventory.ContainerAlchemicalBag",
                                 new ContainerInfo(false, false, true, true, (short) 13));
        compatibilityClasses.put("com.pahimar.ee3.inventory.ContainerAlchemicalChest",
                                 new ContainerInfo(false, false, true, true, (short) 13));
        compatibilityClasses.put("com.pahimar.ee3.inventory.ContainerPortableCrafting",
                                 new ContainerInfo(true, true, false,
                                                   getCompatiblitySlotMapInfo("ee3PortableCraftingSlots")));

        // Ender Storage
        // TODO Row size method. A bit less important because it's a config setting and 2 of 3 options give rowsize 9.
        compatibilityClasses.put("codechicken.enderstorage.storage.item.ContainerEnderItemStorage",
                                 new ContainerInfo(false, false, true));

        // Galacticraft
        compatibilityClasses.put("micdoodle8.mods.galacticraft.core.inventory.GCCoreContainerPlayer",
                                 new ContainerInfo(true, true, false,
                                                   getCompatiblitySlotMapInfo("galacticraftPlayerSlots")));


        try {
            configClasses = CompatibilityConfigLoader.load("config/InvTweaksCompatibility.xml");
        } catch(Exception ex) {
            configClasses = new HashMap<String, ContainerInfo>();
            ex.printStackTrace();
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if(containerClassName == null) {
            if(FMLPlugin.runtimeDeobfEnabled) {
                containerClassName = FMLDeobfuscatingRemapper.INSTANCE.unmap(CONTAINER_CLASS_INTERNAL);
            } else {
                containerClassName = CONTAINER_CLASS_INTERNAL;
            }
            lateInit();
        }

        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode(Opcodes.ASM4);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        cr.accept(cn, 0);

        if("net.minecraft.inventory.Container".equals(transformedName)) {
            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformBaseContainer(cn);

            cn.accept(cw);
            return cw.toByteArray();
        }

        if("net.minecraft.client.gui.inventory.ContainerCreative".equals(transformedName)) {
            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformCreativeContainer(cn);

            cn.accept(cw);
            return cw.toByteArray();
        }

        // Transform classes with explicitly specified information
        ContainerInfo info = standardClasses.get(transformedName);
        if(info != null) {
            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformContainer(cn, info);

            cn.accept(cw);
            return cw.toByteArray();
        }

        if("invtweaks.InvTweaksObfuscation".equals(transformedName)) {
            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            Type containertype = Type.getObjectType(containerClassName);
            for(MethodNode method : (List<MethodNode>) cn.methods) {
                if("isValidChest".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, VALID_CHEST_METHOD, containertype);
                } else if("isValidInventory".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, VALID_INVENTORY_METHOD, containertype);
                } else if("isStandardInventory".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, STANDARD_INVENTORY_METHOD, containertype);
                } else if("getSpecialChestRowSize".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, ROW_SIZE_METHOD, containertype);
                } else if("getContainerSlotMap".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, SLOT_MAP_METHOD, containertype);
                } else if("isLargeChest".equals(method.name)) {
                    ASMHelper.replaceSelfForwardingMethod(method, LARGE_CHEST_METHOD, containertype);
                }
            }

            cn.accept(cw);
            return cw.toByteArray();
        }


        info = configClasses.get(transformedName);
        if(info != null) {
            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformContainer(cn, info);

            cn.accept(cw);
            return cw.toByteArray();
        }

        if(cn.visibleAnnotations != null) {
            for(AnnotationNode annotation : (List<AnnotationNode>) cn.visibleAnnotations) {
                if(annotation != null) {
                    ContainerInfo apiInfo = null;

                    if(ANNOTATION_CHEST_CONTAINER.equals(annotation.desc)) {
                        short rowSize = 9;
                        boolean isLargeChest = false;

                        if(annotation.values != null) {
                            for(int i = 0; i < annotation.values.size(); i += 2) {
                                String valueName = (String) annotation.values.get(i);
                                Object value = annotation.values.get(i + 1);

                                if("rowSize".equals(valueName)) {
                                    rowSize = (short) ((Integer) value).intValue();
                                } else if("isLargeChest".equals(valueName)) {
                                    isLargeChest = (Boolean) value;
                                }
                            }
                        }

                        apiInfo = new ContainerInfo(false, false, true, isLargeChest, rowSize);

                        MethodNode method = findAnnotatedMethod(cn, ANNOTATION_CHEST_CONTAINER_ROW_CALLBACK);

                        if(method != null) {
                            apiInfo.rowSizeMethod = new MethodInfo(Type.getMethodType(method.desc),
                                                                   Type.getObjectType(cn.name), method.name);
                        }
                    } else if(ANNOTATION_INVENTORY_CONTAINER.equals(annotation.desc)) {
                        boolean showOptions = true;

                        if(annotation.values != null) {
                            for(int i = 0; i < annotation.values.size(); i += 2) {
                                String valueName = (String) annotation.values.get(i);
                                Object value = annotation.values.get(i + 1);

                                if("showOptions".equals(valueName)) {
                                    showOptions = (Boolean) value;
                                }
                            }
                        }

                        apiInfo = new ContainerInfo(showOptions, true, false);
                    }

                    if(apiInfo != null) {
                        // Search methods to see if any have the ContainerSectionCallback attribute.
                        MethodNode method = findAnnotatedMethod(cn, ANNOTATION_CONTAINER_SECTION_CALLBACK);

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

        info = compatibilityClasses.get(transformedName);
        if(info != null) {
            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformContainer(cn, info);

            cn.accept(cw);
            return cw.toByteArray();
        }

        if("net.minecraft.client.gui.GuiTextField".equals(transformedName)) {
            FMLRelaunchLog.info("InvTweaks: %s", transformedName);

            transformTextField(cn);

            cn.accept(cw);
            return cw.toByteArray();
        }

        return bytes;
    }

    private MethodNode findAnnotatedMethod(ClassNode cn, String annotationDesc) {
        for(MethodNode method : (List<MethodNode>) cn.methods) {
            if(method.visibleAnnotations != null) {
                for(AnnotationNode methodAnnotation : (List<AnnotationNode>) method.visibleAnnotations) {
                    if(annotationDesc.equals(methodAnnotation.desc)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Alter class to contain information contained by ContainerInfo
     *
     * @param clazz Class to alter
     * @param info  Information used to alter class
     */
    public static void transformContainer(ClassNode clazz, ContainerInfo info) {
        ASMHelper.generateBooleanMethodConst(clazz, STANDARD_INVENTORY_METHOD, info.standardInventory);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_INVENTORY_METHOD, info.validInventory);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, info.validChest);
        ASMHelper.generateBooleanMethodConst(clazz, LARGE_CHEST_METHOD, info.largeChest);

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
    public static void transformBaseContainer(ClassNode clazz) {
        ASMHelper.generateBooleanMethodConst(clazz, STANDARD_INVENTORY_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_INVENTORY_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, LARGE_CHEST_METHOD, false);
        ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, (short) 9);
        ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, "unknownContainerSlots",
                                                   Type.getObjectType("java/util/Map"),
                                                   Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
    }

    public static void transformCreativeContainer(ClassNode clazz) {
        /* FIXME: Reqired methods cannot be compiled until SpecialSource update
        ASMHelper.generateForwardingToStaticMethod(clazz, STANDARD_INVENTORY_METHOD, "containerCreativeIsInventory",
                                                   Type.BOOLEAN_TYPE, Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
        ASMHelper.generateForwardingToStaticMethod(clazz, VALID_INVENTORY_METHOD, "containerCreativeIsInventory",
                                                   Type.BOOLEAN_TYPE, Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
        ASMHelper.generateBooleanMethodConst(clazz, VALID_CHEST_METHOD, false);
        ASMHelper.generateBooleanMethodConst(clazz, LARGE_CHEST_METHOD, false);
        ASMHelper.generateIntegerMethodConst(clazz, ROW_SIZE_METHOD, (short) 9);
        ASMHelper.generateForwardingToStaticMethod(clazz, SLOT_MAP_METHOD, "containerCreativeSlots",
                                                   Type.getObjectType("java/util/Map"),
                                                   Type.getObjectType(SLOT_MAPS_VANILLA_CLASS));
         */
    }

    private static void transformTextField(ClassNode clazz) {
        for(MethodNode method : (List<MethodNode>) clazz.methods) {
            String unmappedName = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(clazz.name, method.name, method.desc);
            String unmappedDesc = FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(method.desc);

            if("func_146195_b".equals(unmappedName) && "(Z)V".equals(unmappedDesc)) {
                InsnList code = method.instructions;
                AbstractInsnNode returnNode = null;
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
                                                         "setTextboxModeStatic", "(Z)V"));

                    FMLRelaunchLog.info("InvTweaks: successfully transformed setFocused/func_146195_b");
                } else {
                    FMLRelaunchLog.severe("InvTweaks: unable to find return in setFocused/func_146195_b");
                }
            }
        }
    }


    public static MethodInfo getCompatiblitySlotMapInfo(String name) {
        return getSlotMapInfo(Type.getObjectType(SLOT_MAPS_MODCOMPAT_CLASS), name, true);
    }

    public static MethodInfo getVanillaSlotMapInfo(String name) {
        return getSlotMapInfo(Type.getObjectType(SLOT_MAPS_VANILLA_CLASS), name, true);
    }

    public static MethodInfo getSlotMapInfo(Type mClass, String name, boolean isStatic) {
        return new MethodInfo(
                Type.getMethodType(Type.getObjectType("java/util/Map"), Type.getObjectType(containerClassName)), mClass,
                name, isStatic);
    }
}
