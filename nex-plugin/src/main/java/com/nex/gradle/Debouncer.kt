package com.nex.gradle

import com.nex.Debounce
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.CtNewMethod


/**
 *  Debouncing enforces that a function not be called again until a certain amount of time has passed without it being called.
 * */
class Debouncer(
    private val destFolder: String,
    private val pool: ClassPool,
    private val clazz: CtClass,
    private val method: CtMethod) {

    fun debounce() {
        //com.nex.Debounce
        if (method.returnType != CtClass.voidType)
            error("@Debounce may be placed only on method which return void. But in this case: ${clazz.simpleName}.${method.name} return ${method.returnType.simpleName}.")

        val runnableClass = clazz.makeNestedClass("Debounce${method.name.capitalize()}Runnable", true)
        runnableClass.addInterface(pool.get("java.lang.Runnable"))

        val debounceField = "_\$_runnableMethodCall${method.name.capitalize()}"
        clazz.addPublicField(runnableClass, debounceField)

        val debounceValue = getDebounceTimeFromAnnotation()

        val originalMethod = CtNewMethod.copy(method, clazz, null)
        originalMethod.name = "${method.name}\$\$"

        val proxyMethod = CtNewMethod.copy(method, clazz, null)


        runnableClass.addPublicField(clazz, "_super")
        originalMethod.parameterTypes.forEachIndexed { index, c ->
            runnableClass.addPublicField(c, "_${index + 1}")
        }

        runnableClass.addDefaultConstructor()

        val proxyBody = """{
            if (@0.${debounceField} != null) com.nex.Nex.nexUIHandler.removeCallbacks(@0.$debounceField);
            if (@0.${debounceField} == null) {
                @0.$debounceField = new ${runnableClass.name}();
                
            }
            @0.${debounceField}._super = @0;
            ${originalMethod.parameterTypes.indicesToString("\n") {
                "@0.${debounceField}._${it + 1} = @${it + 1};"
            }}            
            com.nex.Nex.nexUIHandler.postDelayed(@0.$debounceField, (long)$debounceValue);
        }""".toJavassist()

        clazz.removeMethod(method)
        clazz.addMethod(originalMethod)
        clazz.addMethod(proxyMethod)
        proxyMethod.setBody(proxyBody)

        val runMethod = runnableClass.newMethod("run") {
            """{
                $0._super.${originalMethod.name}(${originalMethod.parameterTypes.indicesToString { "\$0._${it + 1}" }});
                $0._super.$debounceField = null;
                $0._super = null;
                ${originalMethod.parameterTypes.filter { !it.isPrimitive }.indicesToString("\n") { "\$0._${it + 1} = null;" }}
            }""".trimIndent()
        }

        runnableClass.addMethod(runMethod)
        runnableClass.writeFile(destFolder)
        println("GENERATED: ${destFolder}/${runnableClass.name}")
    }

    private fun getDebounceTimeFromAnnotation(): Long {
        val annotation = method.getAnnotation(Debounce::class.java) as Debounce
        return annotation.value
    }
}