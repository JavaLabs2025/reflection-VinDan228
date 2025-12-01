package org.example.generator;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

// выяснить как достаются типы ведь они затираются
// потыкать сеттеры
// попробовать нотейшн препроцессора (как у карима)
// что если коллекции будут параметризированы как "? extends" или "? super"

public class Generator {
    public static final String CLASS = ".class";
    public static final char POINT = '.';
    public static final char STICK = '!';
    public static final char SLASH = '/';
    public static final String BLANK = "";
    public static final String BINARY_TREE_NODE = "BinaryTreeNode";
    public static final String FILE = "file";
    public static final String JAR = "jar";
    public static final String FILE_POINTED = "file:";
    public static final String $ = "$";
    public static final int MAX_BINARY_TREE_DEPTH = 3;
    private final Random random = new Random();

    /**
     * "Маршрутизирует" или самостоятельно проводит генерацию заданного значения/объекта/интерфейса
     */
    public Object generateValueOfType(Class<?> c)
            throws InvocationTargetException, InstantiationException, IllegalAccessException,
            IOException, URISyntaxException, ClassNotFoundException, NoSuchMethodException
    {
        if (c.isInterface()) {
            return generateInterface(c);
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

        if (c.getSimpleName().equals(BINARY_TREE_NODE)) {
            return generateBinaryTreeNode(c, MAX_BINARY_TREE_DEPTH);
        }

        if (!c.isAnnotationPresent(Generatable.class)) {
            throw new IllegalArgumentException("There is no @Generatable annotation in class " + c.getName());
        }
        return generateObject(c);
    }

    /**
     * Генерирует дерево BinaryTreeNode ограниченной глубины.
     */
    private Object generateBinaryTreeNode(Class<?> nodeClass, int depth)
            throws InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException
    {
        Constructor<?> constructor = nodeClass.getDeclaredConstructor(Integer.class, nodeClass, nodeClass);
        constructor.setAccessible(true);

        Integer data;
        if (random.nextBoolean()) {
            data = null;
        } else {
            data = random.nextInt(1000);
        }

        Object left = null;
        Object right = null;

        if (depth > 0) {
            if (random.nextBoolean()) {
                left = generateBinaryTreeNode(nodeClass, depth - 1);
            }
            if (random.nextBoolean()) {
                right = generateBinaryTreeNode(nodeClass, depth - 1);
            }
        }

        return constructor.newInstance(data, left, right);
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
    private Object generateInterface(Class<?> interfaceClass)
            throws InvocationTargetException, InstantiationException, IllegalAccessException,
            IOException, URISyntaxException, ClassNotFoundException, NoSuchMethodException {
        String packageName = interfaceClass.getPackage().getName();
        List<Class<?>> implementations = findImplementations(interfaceClass, packageName);

        if (implementations.isEmpty()) {
            throw new IllegalArgumentException("No @Generatable implementations found for interface " + interfaceClass.getName());
        }

        Class<?> selectedClass = implementations.get(random.nextInt(implementations.size()));
        return generateObject(selectedClass);
    }

    /**
     * Генерирует объекты классов
     */
    private Object generateObject(Class<?> c)
            throws InvocationTargetException, InstantiationException, IllegalAccessException,
            IOException, URISyntaxException, ClassNotFoundException, NoSuchMethodException {
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

            // массивы
            if (paramType.isArray()) {
                Class<?> componentType = paramType.getComponentType();
                int length = random.nextInt(3) + 1;
                Object array = Array.newInstance(componentType, length);
                for (int j = 0; j < length; j++) {
                    Array.set(array, j, generateValueOfType(componentType));
                }
                params[i] = array;
                continue;
            }

            // List, Set и подобное
            if (Collection.class.isAssignableFrom(paramType)) {
                // generic-параметры стираются у объектов, но остаются в сигнатуре конструктора,
                // поэтому достаём их через getGenericParameterTypes()
                Type genericType = selectedConstructor.getGenericParameterTypes()[i];

                // проверка на факт дженерика, чтобы не был простым типом как Integer или String
                if (genericType instanceof ParameterizedType pt) {
                    Type[] actualTypes = pt.getActualTypeArguments();

                    if (actualTypes.length == 1) {

                        Class<?> elementClass = getElementClass(actualTypes);

                        if (elementClass != null) {
                            Collection<Object> collection;
                            if (Set.class.isAssignableFrom(paramType)) {
                                collection = new HashSet<>();
                            } else {
                                collection = new ArrayList<>();
                            }

                            int size = random.nextInt(3) + 1;
                            for (int j = 0; j < size; j++) {
                                collection.add(generateValueOfType(elementClass));
                            }
                            params[i] = collection;
                            continue;
                        }
                    }
                }
            }

            // создаём простую HashMap и заполняем рандомными парами
            if (Map.class.isAssignableFrom(paramType)) {
                Type genericType = selectedConstructor.getGenericParameterTypes()[i];

                if (genericType instanceof ParameterizedType pt) {
                    Type[] actualTypes = pt.getActualTypeArguments();

                    if (actualTypes.length == 2) {
                        Type keyType = actualTypes[0];
                        Type valueType = actualTypes[1];

                        Class<?> keyClass = resolveWildcardClass(keyType);
                        Class<?> valueClass = resolveWildcardClass(valueType);

                        if (keyClass != null && valueClass != null) {
                            Map<Object, Object> map = new HashMap<>();
                            int size = random.nextInt(3) + 1;
                            for (int j = 0; j < size; j++) {
                                Object key = generateValueOfType(keyClass);
                                Object value = generateValueOfType(valueClass);
                                map.put(key, value);
                            }
                            params[i] = map;
                            continue;
                        }
                    }
                }
            }

            // для остальных параметров, в том числе бинарное древо, рекурсивно вызываем генератор
            if (paramType == Integer.class || paramType.getSimpleName().equals(BINARY_TREE_NODE) && random.nextDouble() < 0.3) {
                params[i] = null;
            } else {
                params[i] = generateValueOfType(paramType);
            }
        }

        return selectedConstructor.newInstance(params);
    }

    private static Class<?> getElementClass(Type[] actualTypes) {
        Class<?> elementClass = null;
        Type elementType = actualTypes[0];

        if (elementType instanceof Class<?> ec) {
            elementClass = ec;
        } else if (elementType instanceof WildcardType wt) {
            // поддерживаем ? extends T: берём верхнюю границу, если она класс, то есть T
            Type[] upperBounds = wt.getUpperBounds();
            if (upperBounds.length > 0 && upperBounds[0] instanceof Class<?> ec) {
                elementClass = ec;
            }
            // для ? super T оставляем elementClass == null и не генерируем коллекцию
        }
        return elementClass;
    }

    /**
     * Преобразует Type в Class, поддерживая wildcard (? extends T).
     */
    private Class<?> resolveWildcardClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof WildcardType wt) {
            Type[] upperBounds = wt.getUpperBounds();
            if (upperBounds.length > 0 && upperBounds[0] instanceof Class<?> c) {
                return c;
            }
        }
        return null;
    }

    /**
     * Универсальный метод для поиска всех классов, реализующих интерфейс.
     * Сканирует classpath на предмет классов в пакете
     */
    private List<Class<?>> findImplementations(Class<?> interfaceClass, String packageName)
            throws IOException, URISyntaxException, ClassNotFoundException
    {
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

            if (FILE.equals(protocol)) {
                findClassesInDir(resource, packageName, interfaceClass, implementations);
            } else if (JAR.equals(protocol)) {
                findClassesInJar(resource, packageName, interfaceClass, implementations);
            }
        }
        
        return implementations;
    }

    /**
     * Сканирует репозиторий (типа как мы видим его в IDE) и ищет все классы в пакете
     */
    private void findClassesInDir(URL resource, String packageName, Class<?> interfaceClass, List<Class<?>> implementations)
            throws URISyntaxException, IOException
    {
        Path directory = Paths.get(resource.toURI());
        if (Files.exists(directory) && Files.isDirectory(directory)) {
            Files.walk(directory)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(CLASS))
                .forEach(path -> {
                    String className = packageName + POINT +
                        directory.relativize(path).toString().replace(SLASH, POINT).replace(CLASS, BLANK);
                    try {
                        checkClasses(className, interfaceClass, implementations);
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    /**
     * Сканирует JAR'ник и ищет все классы в пакете
     */
    private void findClassesInJar(URL resource, String packageName, Class<?> interfaceClass, List<Class<?>> implementations)
            throws ClassNotFoundException
    {
        String packagePath = packageName.replace(POINT, SLASH);
        String resourcePath = resource.getPath();
        
        int jarIndex = resourcePath.indexOf(STICK);
        if (jarIndex == -1) {
            return;
        }
        
        String jarPath = resourcePath.substring(resourcePath.startsWith(FILE_POINTED) ? 5 : 0, jarIndex);
        jarPath = URLDecoder.decode(jarPath, StandardCharsets.UTF_8);
        
        try (JarFile jar = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {

                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                if (entryName.startsWith(packagePath) && entryName.endsWith(CLASS) && !entryName.contains($)) {
                    String className = entryName.replace(SLASH, POINT).replace(CLASS, BLANK);
                    checkClasses(className, interfaceClass, implementations);
                }
            }
        } catch (IOException _) {}
    }

    /**
     * Проверяет класс и добавляет его в список, если он норм
     */
    private void checkClasses(String className, Class<?> interfaceClass, List<Class<?>> implementations)
            throws ClassNotFoundException
    {
        Class<?> c = Class.forName(className);
        if (!c.isInterface() && interfaceClass.isAssignableFrom(c) && c.isAnnotationPresent(Generatable.class)) {
            implementations.add(c);
        }
    }
}
