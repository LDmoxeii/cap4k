@file:JvmName("ClassUtils")

package com.only4.cap4k.ddd.core.share.misc

import org.springframework.aop.support.AopUtils
import org.springframework.cglib.beans.BeanCopier
import org.springframework.core.ResolvableType
import org.springframework.core.convert.converter.Converter
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.util.function.Predicate

/**
 * 获取指定类或接口泛型参数类型
 *
 * @param obj
 * @param typeArgumentIndex
 * @param superClasses
 * @return
 */
fun resolveGenericTypeClass(
    obj: Any,
    typeArgumentIndex: Int,
    vararg superClasses: Class<*>
): Class<*> {
    val clazz = AopUtils.getTargetClass(obj)
    return resolveGenericTypeClass(
        clazz,
        typeArgumentIndex,
        *superClasses
    )
}

/**
 * 获取指定类或接口泛型参数类型
 *
 * @param clazz
 * @param typeArgumentIndex
 * @param superClasses
 * @return
 */
fun resolveGenericTypeClass(
    clazz: Class<*>,
    typeArgumentIndex: Int,
    vararg superClasses: Class<*>
): Class<*> {
    val parameterizedType = if (superClasses.any {
            it == ResolvableType.forType(clazz.genericSuperclass).toClass()
        }) clazz.genericSuperclass as ParameterizedType
    else {
        clazz.genericInterfaces.asSequence()
            .mapNotNull { it as ParameterizedType }
            .firstOrNull { type ->
                superClasses.any { it == ResolvableType.forType(type).toClass() }
            }
    }
    return parameterizedType?.let {
        ResolvableType.forType(it.actualTypeArguments[typeArgumentIndex]).toClass()
    } ?: Any::class.java
}

/**
 * 查找方法
 *
 * @param clazz           查找基于类型
 * @param name            方法名称
 * @param methodPredicate
 * @return
 */
fun findMethod(clazz: Class<*>, name: String, methodPredicate: Predicate<Method>?): Method? {
    return clazz.declaredMethods.asSequence()
        .filter { it.name == name }
        .filter { methodPredicate?.test(it) ?: true }
        .firstOrNull()
}

/**
 * 查找构造器
 *
 * @param clazz                 查找基于类型
 * @param constructorPredicate  构造器谓词条件
 * @return
 */
fun findConstructor(clazz: Class<*>, constructorPredicate: Predicate<Constructor<*>>?): Constructor<*>? {
    return clazz.declaredConstructors.asSequence()
        .filter { constructorPredicate?.test(it) ?: true }
        .firstOrNull()
}

/**
 * 获取 Converter对象
 *
 * @param srcClass       源类型
 * @param destClass      模板类型
 * @param converterClass 转换类
 * @return
 */
@Suppress("UNCHECKED_CAST")
fun newConverterInstance(
    srcClass: Class<*>,
    destClass: Class<*>,
    converterClass: Class<*>? = Unit::class.java
): Converter<Any, Any> {
    var converter: Converter<*, *>? =
        if (converterClass == null || converterClass == Unit::class.java) null
        else converterClass.getConstructor()
            .newInstance() as Converter<*, *>

    findMethod(destClass, "convert") {
        it.parameterCount == 1
                && srcClass.isAssignableFrom(resolveGenericTypeClass(destClass, 0, Converter::class.java))
                && destClass.isAssignableFrom(resolveGenericTypeClass(destClass, 1, Converter::class.java))
    }?.let {
        return Converter<Any, Any> { src ->
            it.invoke(destClass.getConstructor().newInstance(), src)
        }
    }

    if (converter == null) {
        val copier = BeanCopier.create(
            srcClass,
            destClass, false
        )
        converter = Converter<Any, Any> { src ->
            val dest = destClass.getConstructor().newInstance()
            copier.copy(src, dest, null)
            dest
        }
    }
    return converter as Converter<Any, Any>
}
