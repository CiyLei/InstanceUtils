import com.google.gson.reflect.TypeToken;
import java.lang.reflect.*;
import java.util.*;

public class InstanceUtils {

    public static Object newInstance(Type type) {
        return createClass(TypeToken.get(type), new HashSet<>(), null);
    }

    /**
     * 根据类，创建一个都是默认值的类
     * 内部类的话请添加static，成为静态内部类
     *
     * @param typeToken
     * @param hostClasss 记录宿主Class，防止死循环递归
     * @return
     */
    private static Object createClass(TypeToken typeToken, Set<String> hostClasss, Type hostClassGenericType) {
        // 排除List套List的情况，因为这种因为不视为死循环，以下会默认处理这个迭代的情况的
        hostClasss.remove(List.class.getName());
        // getRawType 不带泛型 getType带泛型
        if (Iterable.class.isAssignableFrom(typeToken.getRawType())) {
            // 迭代类型
            hostClasss.remove(typeToken.getType().getTypeName());
            Type listType = typeToken.getType();
            if (listType instanceof ParameterizedType && ((ParameterizedType) listType).getActualTypeArguments().length == 1) {
                // 读取List泛型实际类型
                Type listClassType = ((ParameterizedType) listType).getActualTypeArguments()[0];
                List list = new ArrayList();
                list.add(createClass(TypeToken.get(listClassType), hostClasss, hostClassGenericType));
                return list;
            }
        } else if(TypeVariable.class.isAssignableFrom(typeToken.getType().getClass())){
            // 当前字段就是泛型 T
            hostClasss.remove(typeToken.getType().getTypeName());
            return createClass(TypeToken.get(hostClassGenericType), hostClasss, null);
        } else if(typeToken.getRawType().isArray()) {
            // 数组类型
            hostClasss.remove(typeToken.getType().getTypeName());
            List list = new ArrayList();
            list.add(createClass(TypeToken.get(boxIfPrimitive(typeToken.getRawType().getComponentType())), hostClasss, hostClassGenericType));
            return list;
        }else if (isBaseType(typeToken.getRawType())) {
            // 如果是基本类型的话
            hostClasss.remove(typeToken.getType().getTypeName());
            return baseTypeDefaultValue(typeToken.getRawType());
        }
        if (typeToken.getRawType().isAnnotation() || typeToken.getRawType().isInterface()) {
            // 如果是注解类或者接口的话
            return null;
        }
        // 开始构造默认值的实例
        Object result = null;
        for (Constructor constructor : typeToken.getRawType().getDeclaredConstructors()) {
            // 有空构造就创建实例
            if (constructor.getParameterCount() == 0) {
                try {
                    result = constructor.newInstance();
                    break;
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
        if (result == null) {
            // 如果没有构建方法就创建实例
            if (typeToken.getRawType().getConstructors().length == 0) {
                try {
                    result = typeToken.getRawType().newInstance();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        if (result != null) {
            for (Field field : getAllField(typeToken.getRawType())) {
                try {
                    Type type = typeToken.getType();
                    // 如果这个类有泛型的话
                    if (type instanceof ParameterizedType) {
                        // 如果有多个泛型的话，不能区分该字段是那个泛型的，所以只处理一个泛型
                        if (((ParameterizedType) typeToken.getType()).getActualTypeArguments().length == 1) {
                            // 当前类的泛型实际类型
                            Type classGenericType = ((ParameterizedType) type).getActualTypeArguments()[0];
                            // 防止死循环递归
                            if (!hostClasss.contains(TypeToken.get(classGenericType).getType().getTypeName())) {
                                // 如果当前字段就是泛型T的话
                                if (field.getGenericType() instanceof TypeVariable) {
                                    hostClasss.add(TypeToken.get(classGenericType).getType().getTypeName());
                                    field.set(result, createClass(TypeToken.get(classGenericType), hostClasss, null));
                                    continue;
                                }
                                // 如果当前字段有泛型
                                if (ParameterizedType.class.isAssignableFrom(field.getGenericType().getClass())) {
                                    TypeToken fieldGenericTypeToken = TypeToken.get(field.getGenericType());
                                    // 同样，也处理只有一个泛型的情况
                                    if (((ParameterizedType) fieldGenericTypeToken.getType()).getActualTypeArguments().length == 1) {
                                        // 当前字段的泛型实际类型
                                        hostClasss.add(fieldGenericTypeToken.getType().getTypeName());
                                        field.set(result, createClass(fieldGenericTypeToken, hostClasss, classGenericType));
                                        continue;
                                    }
                                }
                            }
                        }
                    }
                    // 无泛型的普通类
                    if (!hostClasss.contains(field.getType().getName())) {
                        // 防止自己引用自己无限循环
                        hostClasss.add(field.getType().getName());
                        field.set(result, createClass(TypeToken.get(field.getGenericType()), hostClasss, null));
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    private static List<Field> getAllField(Class clazz) {
        if (clazz != Object.class) {
            // 添加自己的字段和父类的字段
            List<Field> result = new ArrayList<>(Arrays.asList(clazz.getDeclaredFields()));
            result.addAll(getAllField(clazz.getSuperclass()));
            result.forEach(it -> it.setAccessible(true));
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * 是否是基本类型
     *
     * @return
     */
    private static Boolean isBaseType(Class mClass) {
        if (mClass == Integer.class) return true;
        if (mClass == String.class) return true;
        if (mClass == Byte.class) return true;
        if (mClass == Long.class) return true;
        if (mClass == Double.class) return true;
        if (mClass == Float.class) return true;
        if (mClass == Character.class) return true;
        if (mClass == Short.class) return true;
        if (mClass == Boolean.class) return true;
        if (mClass == int.class) return true;
        if (mClass == byte.class) return true;
        if (mClass == long.class) return true;
        if (mClass == double.class) return true;
        if (mClass == float.class) return true;
        if (mClass == char.class) return true;
        if (mClass == short.class) return true;
        if (mClass == boolean.class) return true;
        return false;
    }

    /**
     * 基本类型的默认值
     * @param mClass
     * @return
     */
    private static Object baseTypeDefaultValue(Class mClass) {
        if (mClass == Integer.class || mClass == int.class) return 0;
        if (mClass == String.class) return "";
        if (mClass == Byte.class || mClass == byte.class) return Byte.valueOf("0");
        if (mClass == Long.class || mClass == long.class) return Long.valueOf("0");
        if (mClass == Double.class || mClass == double.class) return Double.valueOf("0");
        if (mClass == Float.class || mClass == float.class) return Float.valueOf("0.0");
        if (mClass == Character.class || mClass == char.class) return '\u0000';
        if (mClass == Short.class || mClass == short.class) return Short.valueOf("0");
        if (mClass == Boolean.class || mClass == boolean.class) return false;
        return 0;
    }

    private static Class<?> boxIfPrimitive(Class<?> type) {
        if (boolean.class == type) return Boolean.class;
        if (byte.class == type) return Byte.class;
        if (char.class == type) return Character.class;
        if (double.class == type) return Double.class;
        if (float.class == type) return Float.class;
        if (int.class == type) return Integer.class;
        if (long.class == type) return Long.class;
        if (short.class == type) return Short.class;
        return type;
    }
}
