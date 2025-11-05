package org.example;

import org.example.classes.*;
import org.example.generator.Generator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class GenerateExample {
    public static void main(String[] args) {
        Generator gen = new Generator();
        
        System.out.println("======== Test of Generator ========\n");
        testGeneration(gen, Example.class, "1. Class Example");
        testGeneration(gen, Product.class, "2. Class Product");
        testGeneration(gen, Shape.class, "3. Interface Shape"); // Должен найти либо Rectangle, либо Triangle
        testGeneration(gen, Rectangle.class, "4. Class Rectangle");
        testGeneration(gen, Triangle.class, "5. Class Triangle");
        testGeneration(gen, BinaryTreeNode.class, "6. Recursive struture BinaryTreeNode");
        testGeneration(gen, Cart.class, "7. Class with collection Cart");
    }
    
    private static void testGeneration(Generator gen, Class<?> c, String testName) {
        System.out.println("====================================");
        System.out.println(testName);
        System.out.println("====================================");
        
        try {
            Object generated = gen.generateValueOfType(c);
            printObjectInfo(generated);
            System.out.println("\n\tGOOD EXODUS!\n");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("\n\tBAD EXODUS: \n" + e.getMessage() + "\n");
        }
    }
    
    private static void printObjectInfo(Object obj) {
        if (obj == null) {
            System.out.println("!Null object!");
            return;
        }
        
        Class<?> c = obj.getClass();
        
        System.out.println("Class: " + c.getName());
        System.out.println("Class name: " + c.getSimpleName());
        
        Class<?>[] interfaces = c.getInterfaces();
        if (interfaces.length > 0) {
            System.out.println("Interfaces:");
            for (Class<?> anInterface : interfaces) {
                System.out.println("- " + anInterface.getSimpleName());
            }
        }
        
        System.out.println("Fields:");
        Field[] fields = c.getDeclaredFields();
        if (fields.length == 0) {
            System.out.println("- !No fields in class!");
        } else {
            for (Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    String valueStr = formatValue(value);
                    System.out.println("- " + Modifier.toString(field.getModifiers()) +
                                     " " + field.getType().getSimpleName() +
                                     " " + field.getName() + " = " + valueStr);
                } catch (IllegalAccessException _) {}
            }
        }
        
        System.out.println("Methods calls and results:");
        Method[] methods = c.getDeclaredMethods();
        boolean hasMethods = false;
        
        for (Method method : methods) {
            if (method.getParameterCount() == 0 &&
                (method.getName().startsWith("get") || 
                 method.getName().equals("getArea") || 
                 method.getName().equals("getPerimeter") ||
                 method.getName().equals("toString"))) {
                
                method.setAccessible(true);
                try {
                    Object result = method.invoke(obj);
                    String resultStr = formatValue(result);
                    System.out.println("- " + method.getName() + " -> " + resultStr);
                    hasMethods = true;
                } catch (Exception e) {
                    System.out.println("- " + method.getName() + " -> error: " + e.getMessage());
                    hasMethods = true;
                }
            }
        }

        if (!hasMethods) {
            System.out.println("- !No available methods!");
        }
        
        try {
            Method toStringMethod = c.getMethod("toString");
            String toStringResult = (String) toStringMethod.invoke(obj);
            System.out.println("toString(): " + toStringResult);
        } catch (Exception _) {}
    }
    
    private static String formatValue(Object value) {
        switch (value) {
            case null -> {
                return "null";
            }
            case String s -> {
                return "\"" + value + "\"";
            }
            case List list -> {
                return "List[" + list.size() + "] = " + list;
            }
            default -> {}
        }

        if (value.getClass().isArray()) {
            return value.getClass().getSimpleName() + " array";
        }
        
        if (!isPrimitiveOrWrapper(value.getClass())) {
            return value.getClass().getSimpleName() + "@" + 
                   Integer.toHexString(System.identityHashCode(value));
        }
        
        return value.toString();
    }
    
    private static boolean isPrimitiveOrWrapper(Class<?> c) {
        return c.isPrimitive() || 
               c == String.class ||
               c == Integer.class ||
               c == int.class ||
               c == Double.class ||
               c == double.class ||
               c == Long.class ||
               c == long.class ||
               c == Float.class ||
               c == float.class ||
               c == Byte.class ||
               c == byte.class ||
               c == Short.class ||
               c == short.class ||
               c == Character.class ||
               c == char.class ||
               c == Boolean.class ||
               c == boolean.class;
    }
}