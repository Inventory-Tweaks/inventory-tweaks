package invtweaks.forge.asm;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.Map;


public class ContainerTransformer implements IClassTransformer {
    private static Map<String, ContainerInfo> standardClasses = new HashMap<String, ContainerInfo>();

    public ContainerTransformer() {
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
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        FMLRelaunchLog.info(String.format("%s = %s", name, transformedName));

        if("net.minecraft.inventory.Container".equals(transformedName)) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM4);
            cr.accept(cn, 0);

            transformBaseContainer(cn);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        // Transform classes with explicitly specified information
        ContainerInfo info = standardClasses.get(transformedName);
        if(info != null) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM4);
            cr.accept(cn, 0);

            transformContainer(cn, info);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        if("invtweaks.InvTweaksObfuscation".equals(transformedName)) {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM4);
            cr.accept(cn, 0);

            Type containertype =
                    Type.getObjectType(FMLDeobfuscatingRemapper.INSTANCE.unmap("net/minecraft/inventory/Container"));
            for(MethodNode method : cn.methods) {
                if("isValidChest".equals(method.name)) {
                    replaceSelfForwardingMethod(method, "invtweaks$validChest", containertype);
                } else if("isValidInventory".equals(method.name)) {
                    replaceSelfForwardingMethod(method, "invtweaks$validInventory", containertype);
                } else if("isStandardInventory".equals(method.name)) {
                    replaceSelfForwardingMethod(method, "invtweaks$standardInventory", containertype);
                } else if("getSpecialChestRowSize".equals(method.name)) {
                    replaceSelfForwardingMethod(method, "invtweaks$rowSize", containertype);
                } else if("getContainerSlotMap".equals(method.name)) {
                    replaceSelfForwardingMethod(method, "invtweaks$slotMap", containertype);
                }
            }

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }

        return bytes;
    }

    /**
     * Alter class to contain information contained by ContainerInfo
     *
     * @param clazz Class to alter
     * @param info  Information used to alter class
     */
    public static void transformContainer(ClassNode clazz, ContainerInfo info) {
        generateBooleanMethodConst(clazz, "invtweaks$standardInventory", info.standardInventory);
        generateBooleanMethodConst(clazz, "invtweaks$validInventory", info.validInventory);
        generateBooleanMethodConst(clazz, "invtweaks$validChest", info.validChest);
        generateIntegerMethodConst(clazz, "invtweaks$rowSize", info.rowSize);
        if(info.slotMapMethod.isStatic) {
            generateForwardingToStaticMethod(clazz, "invtweaks$slotMap", info.slotMapMethod.methodName,
                                             info.slotMapMethod.methodType.getReturnType(),
                                             info.slotMapMethod.methodClass,
                                             info.slotMapMethod.methodType.getArgumentTypes()[0]);
        } else {
            generateSelfForwardingMethod(clazz, "invtweaks$slotMap", info.slotMapMethod.methodName,
                                         info.slotMapMethod.methodType);
        }
    }


    /**
     * Alter class to contain default implementations of added methods.
     *
     * @param clazz Class to alter
     */
    public static void transformBaseContainer(ClassNode clazz) {
        generateBooleanMethodConst(clazz, "invtweaks$standardInventory", false);
        generateDefaultInventoryCheck(clazz);
        generateBooleanMethodConst(clazz, "invtweaks$validChest", false);
        generateIntegerMethodConst(clazz, "invtweaks$rowSize", (short) 9);
        generateForwardingToStaticMethod(clazz, "invtweaks$slotMap", "unknownContainerSlots",
                                         Type.getObjectType("java/util/Map"),
                                         Type.getObjectType("invtweaks/VanillaSlotMaps"));
    }

    /**
     * Generate a new method "boolean invtweaks$validInventory()", returning true if the size of the container is large
     * enough to hold the player inventory.
     *
     * @param clazz Class to add method to
     */
    public static void generateDefaultInventoryCheck(ClassNode clazz) {
        MethodNode method =
                new MethodNode(Opcodes.ASM4, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "invtweaks$validInventory",
                               "()Z", null, null);
        InsnList code = method.instructions;

        LabelNode start = new LabelNode();
        code.add(start);

        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETFIELD, clazz.name, "field_75151_b", "Ljava/util/List;"));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "size", "()I"));
        code.add(new IntInsnNode(Opcodes.BIPUSH, 36)); // TODO: Load Static InvTweaksConst.INVENTORY_SIZE

        LabelNode l1 = new LabelNode();
        code.add(new JumpInsnNode(Opcodes.IF_ICMPLE, l1));
        code.add(new InsnNode(Opcodes.ICONST_1));

        LabelNode l2 = new LabelNode();
        code.add(new JumpInsnNode(Opcodes.GOTO, l2));

        code.add(l1);
        code.add(new InsnNode(Opcodes.ICONST_0));

        code.add(l2);
        code.add(new InsnNode(Opcodes.IRETURN));

        LabelNode end = new LabelNode();
        code.add(end);

        method.localVariables
              .add(new LocalVariableNode("this", Type.getObjectType(clazz.name).getDescriptor(), null, start, end, 0));

        clazz.methods.add(method);
    }

    /**
     * Generate a new method "boolean name()", returning a constant value
     *
     * @param clazz  Class to add method to
     * @param name   Name of method
     * @param retval Return value of method
     */
    public static void generateBooleanMethodConst(ClassNode clazz, String name, boolean retval) {
        MethodNode method =
                new MethodNode(Opcodes.ASM4, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name, "()Z", null, null);
        InsnList code = method.instructions;

        code.add(new InsnNode(retval ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));

        clazz.methods.add(method);
    }

    /**
     * Generate a new method "int name()", returning a constant value
     *
     * @param clazz  Class to add method to
     * @param name   Name of method
     * @param retval Return value of method
     */
    public static void generateIntegerMethodConst(ClassNode clazz, String name, short retval) {
        MethodNode method =
                new MethodNode(Opcodes.ASM4, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name, "()I", null, null);
        InsnList code = method.instructions;

        // Probably doesn't make a huge difference, but use BIPUSH if the value is small enough.
        if(retval >= Byte.MIN_VALUE && retval <= Byte.MAX_VALUE) {
            code.add(new IntInsnNode(Opcodes.BIPUSH, retval));
        } else {
            code.add(new IntInsnNode(Opcodes.SIPUSH, retval));
        }
        code.add(new InsnNode(Opcodes.IRETURN));

        clazz.methods.add(method);
    }

    /**
     * Generate a forwarding method of the form "T name() { return this.forward(); }
     *
     * @param clazz       Class to generate new method on
     * @param name        Name of method to generate
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     */
    public static void generateSelfForwardingMethod(ClassNode clazz, String name, String forwardname, Type rettype) {
        MethodNode method = new MethodNode(Opcodes.ASM4, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name,
                                           "()" + rettype.getDescriptor(), null, null);

        populateSelfForwardingMethod(method, forwardname, rettype, Type.getObjectType(clazz.name));

        clazz.methods.add(method);
    }

    /**
     * Generate a forwarding method of the form "static T name(S object) { return object.forward(); }
     *
     * @param clazz       Class to generate new method on
     * @param name        Name of method to generate
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     */
    public static void generateStaticForwardingMethod(ClassNode clazz, String name, String forwardname, Type rettype,
                                                      Type argtype) {
        MethodNode method =
                new MethodNode(Opcodes.ASM4, Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name,
                               "()" + rettype.getDescriptor(), null, null);

        populateSelfForwardingMethod(method, forwardname, rettype, argtype);

        clazz.methods.add(method);
    }

    /**
     * Generate a forwarding method of the form "T name() { return Class.forward(this); }
     *
     * @param clazz       Class to generate new method on
     * @param name        Name of method to generate
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     */
    public static void generateForwardingToStaticMethod(ClassNode clazz, String name, String forwardname, Type rettype,
                                                        Type fowardtype) {
        MethodNode method = new MethodNode(Opcodes.ASM4, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name,
                                           "()" + rettype.getDescriptor(), null, null);

        populateForwardingToStaticMethod(method, forwardname, rettype, Type.getObjectType(clazz.name), fowardtype);

        clazz.methods.add(method);
    }

    /**
     * Generate a forwarding method of the form "T name() { return Class.forward(this); }
     *
     * @param clazz       Class to generate new method on
     * @param name        Name of method to generate
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     * @param thistype    Type to treat 'this' as for overload searching purposes
     */
    public static void generateForwardingToStaticMethod(ClassNode clazz, String name, String forwardname, Type rettype,
                                                        Type fowardtype, Type thistype) {
        MethodNode method = new MethodNode(Opcodes.ASM4, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name,
                                           "()" + rettype.getDescriptor(), null, null);

        populateForwardingToStaticMethod(method, forwardname, rettype, thistype, fowardtype);

        clazz.methods.add(method);
    }


    /**
     * Replace a method's code with a forward to another method on itself (or the first argument of a static method as
     * the argument takes the place of this)
     *
     * @param method      Method to replace code of
     * @param forwardname Name of method to forward to
     * @param thistype    Type of object method is being replaced on
     */
    public static void replaceSelfForwardingMethod(MethodNode method, String forwardname, Type thistype) {
        Type methodType = Type.getMethodType(method.desc);

        method.instructions.clear();

        populateSelfForwardingMethod(method, forwardname, methodType.getReturnType(), thistype);
    }


    /**
     * Generate a forwarding method of the form "T name(S object) { return object.forward(); }
     *
     * @param clazz       Class to generate new method on
     * @param name        Name of method to generate
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     * @param argtype     Type of object to call method on
     */
    public static void generateForwardingMethod(ClassNode clazz, String name, String forwardname, Type rettype,
                                                Type argtype) {
        MethodNode method = new MethodNode(Opcodes.ASM4, Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, name,
                                           "()" + rettype.getDescriptor(), null, null);

        populateForwardingMethod(method, forwardname, rettype, argtype, Type.getObjectType(clazz.name));

        clazz.methods.add(method);
    }

    /**
     * Replace a method's code with a forward to an method on its first argument
     *
     * @param method      Method to replace code of
     * @param forwardname Name of method to forward to
     * @param thistype    Type of object method is being replaced on
     */
    public static void replaceForwardingMethod(MethodNode method, String forwardname, Type thistype) {
        Type methodType = Type.getMethodType(method.desc);

        method.instructions.clear();

        populateForwardingMethod(method, forwardname, methodType.getReturnType(), methodType.getArgumentTypes()[0],
                                 thistype);
    }

    /**
     * Populate a forwarding method of the form "T name() { return Class.forward(this); }"
     *
     * @param method      Method to generate code for
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     * @param thistype    Type of object method is being generated on
     * @param forwardtype Type to forward method to
     */
    public static void populateForwardingToStaticMethod(MethodNode method, String forwardname, Type rettype,
                                                        Type thistype, Type forwardtype) {
        InsnList code = method.instructions;

        code.add(new VarInsnNode(thistype.getOpcode(Opcodes.ILOAD), 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, forwardtype.getInternalName(), forwardname,
                                    Type.getMethodDescriptor(rettype, thistype)));
        code.add(new InsnNode(rettype.getOpcode(Opcodes.IRETURN)));
    }

    /**
     * Populate a forwarding method of the form "T name() { return this.forward(); }" This is also valid for methods of
     * the form "static T name(S object) { return object.forward() }"
     *
     * @param method      Method to generate code for
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     * @param thistype    Type of object method is being generated on
     */
    public static void populateSelfForwardingMethod(MethodNode method, String forwardname, Type rettype,
                                                    Type thistype) {
        InsnList code = method.instructions;

        code.add(new VarInsnNode(thistype.getOpcode(Opcodes.ILOAD), 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, thistype.getInternalName(), forwardname,
                                    "()" + rettype.getDescriptor()));
        code.add(new InsnNode(rettype.getOpcode(Opcodes.IRETURN)));
    }


    /**
     * Populate a forwarding method of the form "T name(S object) { return object.forward(); }"
     *
     * @param method      Method to generate code for
     * @param forwardname Name of method to call
     * @param rettype     Return type of method
     * @param argtype     Type of object to call method on
     * @param thistype    Type of object method is being generated on
     */
    public static void populateForwardingMethod(MethodNode method, String forwardname, Type rettype, Type argtype,
                                                Type thistype) {
        InsnList code = method.instructions;

        code.add(new VarInsnNode(argtype.getOpcode(Opcodes.ILOAD), 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, argtype.getInternalName(), forwardname,
                                    "()" + rettype.getDescriptor()));
        code.add(new InsnNode(rettype.getOpcode(Opcodes.IRETURN)));
    }

    private MethodInfo getVanillaSlotMapInfo(String name) {
        return getSlotMapInfo(Type.getObjectType("invtweaks/VanillaSlotMaps"), name, true);
    }

    private MethodInfo getSlotMapInfo(Type mClass, String name, boolean isStatic) {
        return new MethodInfo(Type.getMethodType(
                Type.getObjectType("java/util/Map"),
                Type.getObjectType(FMLDeobfuscatingRemapper.INSTANCE.unmap("net/minecraft/inventory/Container"))),
                              mClass, name, true);
    }

    class MethodInfo {
        Type methodType;
        Type methodClass;
        String methodName;
        boolean isStatic = false;

        MethodInfo(Type mType, Type mClass, String name) {
            methodType = mType;
            methodClass = mClass;
            methodName = name;
        }

        MethodInfo(Type mType, Type mClass, String name, boolean stat) {
            methodType = mType;
            methodClass = mClass;
            methodName = name;
            isStatic = stat;
        }
    }

    class ContainerInfo {
        boolean standardInventory = false;
        boolean validInventory = false;
        boolean validChest = false;
        short rowSize = 9;
        MethodInfo slotMapMethod = getVanillaSlotMapInfo("unknownContainerSlots");

        ContainerInfo() {
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh, MethodInfo slotMap) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
            slotMapMethod = slotMap;
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh, short rowS) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
            rowSize = rowS;
        }

        ContainerInfo(boolean standard, boolean validInv, boolean validCh, short rowS, MethodInfo slotMap) {
            standardInventory = standard;
            validInventory = validInv;
            validChest = validCh;
            rowSize = rowS;
            slotMapMethod = slotMap;
        }
    }
}
