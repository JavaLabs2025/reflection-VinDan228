package org.example.generator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class Generator {
    public static final String CLASS = ".class";
    public static final char POINT = '.';
    public static final char STICK = '!';
    public static final char SLASH = '/';
    public static final String BLANK = "";
    public static final String BINARY_TREE_NODE = "BinaryTreeNode";
    private final Random random = new Random();

    /**
     * "Маршрутизирует" или самостоятельно проводит генерацию заданного значения/объекта/интерфейса
     */
    public Object generateValueOfType(Class<?> c) throws InvocationTargetException, InstantiationException, IllegalAccessException, IOException, URISyntaxException, ClassNotFoundException {
        if (c.isInterface()) {
            return generateForInterface(c);
        }

        if (c == String.class) {
            return generateString();
        }
        if (c == Character.class || c == char.class) {
            return (char) (random.nextInt(26) + 'a');
        }
        if (c == Integer.class || c == int.class) {
            return random.nextInt(1000);
        }
        if (c == Byte.class || c == byte.class) {
            return (byte) random.nextInt(256);
        }
        if (c == Short.class || c == short.class) {
            return (short) random.nextInt(Short.MAX_VALUE);
        }
        if (c == Long.class || c == long.class) {
            return random.nextLong();
        }
        if (c == Float.class || c == float.class) {
            return random.nextFloat() * 1000;
        }
        if (c == Double.class || c == double.class) {
            return random.nextDouble() * 1000;
        }
        if (c == Boolean.class || c == boolean.class) {
            return random.nextBoolean();
        }

        if (!c.isAnnotationPresent(Generatable.class)) {
            throw new IllegalArgumentException("There is no @Generatable annotation in class " + c.getName());
        }
        return generateObject(c);
    }

    /**
     * Генерирует случайную строку от 1 до 64 символов
     */
    private String generateString() {
        int l = random.nextInt(64) + 1;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l; i++) {
            char c = (char) (random.nextInt(26) + 'a');
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Генерирует реализацию интерфейса
     */
    private Object generateForInterface(Class<?> interfaceClass) throws InvocationTargetException, InstantiationException, IllegalAccessException, IOException, URISyntaxException, ClassNotFoundException {
        String packageName = interfaceClass.getPackage().getName();
        List<Class<?>> implementations = findImplementations(interfaceClass, packageName);

        if (implementations.isEmpty()) {
            throw new IllegalArgumentException("No @Generatable implementations found for interface " + interfaceClass.getName());
        }

        Class<?> selectedClass = implementations.get(random.nextInt(implementations.size()));
        return generateObject(selectedClass);
    }

    /**
     * Универсальный метод для поиска всех классов, реализующих интерфейс.
     * Сканирует classpath на предмет классов в пакете
     */
    private List<Class<?>> findImplementations(Class<?> interfaceClass, String packageName) throws IOException, URISyntaxException, ClassNotFoundException {
        List<Class<?>> implementations = new ArrayList<>();
        String packagePath = packageName.replace(POINT, SLASH);

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }

        Enumeration<URL> resources = classLoader.getResources(packagePath);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();

            if ("file".equals(protocol)) {
                findClassesInDirectory(resource, packageName, interfaceClass, implementations);
            } else if ("jar".equals(protocol)) {
                findClassesInJar(resource, packageName, interfaceClass, implementations);
            }
        }
        
        return implementations;
    }

    /**
     * Сканирует директорию и находит все классы в пакете
     */
    private void findClassesInDirectory(URL resource, String packageName, Class<?> interfaceClass, List<Class<?>> implementations) throws URISyntaxException, IOException {
        Path directory = Paths.get(resource.toURI());
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(CLASS))
                .forEach(path -> {
                    String className = packageName + POINT +
                        directory.relativize(path).toString().replace(SLASH, POINT).replace(CLASS, BLANK);
                    try {
                        checkAndAddClass(className, interfaceClass, implementations);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    /**
     * Сканирует JAR'ник и находит все классы в пакете
     */
    private void findClassesInJar(URL resource, String packageName, Class<?> interfaceClass, List<Class<?>> implementations) throws ClassNotFoundException {
        String packagePath = packageName.replace(POINT, SLASH);
        String resourcePath = resource.getPath();
        
        int jarIndex = resourcePath.indexOf(STICK);
        if (jarIndex == -1) {
            return;
        }
        
        String jarPath = resourcePath.substring(resourcePath.startsWith("file:") ? 5 : 0, jarIndex);
        jarPath = java.net.URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
        
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if (entryName.startsWith(packagePath) && entryName.endsWith(CLASS) && !entryName.contains("$")) {
                    String className = entryName.replace(SLASH, POINT).replace(CLASS, BLANK);
                    checkAndAddClass(className, interfaceClass, implementations);
                }
            }
        } catch (IOException _) {}
    }

    /**
     * Проверяет класс и добавляет его в список, если он норм
     */
    private void checkAndAddClass(String className, Class<?> interfaceClass, List<Class<?>> implementations) throws ClassNotFoundException {
        Class<?> c = Class.forName(className);
        if (!c.isInterface() && interfaceClass.isAssignableFrom(c) && c.isAnnotationPresent(Generatable.class)) {
            implementations.add(c);
        }
    }

    private Object generateObject(Class<?> c) throws InvocationTargetException, InstantiationException, IllegalAccessException, IOException, URISyntaxException, ClassNotFoundException {
        Constructor<?>[] constructors = c.getDeclaredConstructors();
        
        if (constructors.length == 0) {
            throw new IllegalArgumentException("no constructors for class " + c.getName());
        }

        Constructor<?> selectedConstructor = constructors[random.nextInt(constructors.length)];
        selectedConstructor.setAccessible(true);

        Class<?>[] paramTypes = selectedConstructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            
            if (paramType == List.class) {
                Type genericType = selectedConstructor.getGenericParameterTypes()[i];
                
                // instanceof предложила idea, не я)
                if (genericType instanceof ParameterizedType pt) {
                    Type[] actualTypes = pt.getActualTypeArguments();
                    
                    if (actualTypes.length > 0 && actualTypes[0] instanceof Class<?> elementType) {
                        List<Object> list = new ArrayList<>();
                        
                        int listSize = random.nextInt(3) + 1;
                        
                        for (int j = 0; j < listSize; j++) {
                            list.add(generateValueOfType(elementType));
                        }
                        params[i] = list;
                        continue;
                    }
                }
            }
            
            if ((paramType == Integer.class || paramType.getName().contains(BINARY_TREE_NODE)) && random.nextDouble() < 0.3) {
                params[i] = null;
            } else {
                params[i] = generateValueOfType(paramType);
            }
        }

        return selectedConstructor.newInstance(params);
    }
}
