package com.only4.cap4k.gradle.codegen.velocity

import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.apache.velocity.exception.ParseErrorException
import org.apache.velocity.exception.ResourceNotFoundException
import java.io.StringWriter

/**
 * Velocity 模板渲染器
 *
 * 职责:
 * 1. 渲染外部 .vm 模板文件
 * 2. 渲染内联模板字符串
 * 3. 统一异常处理
 *
 * @author cap4k-codegen
 * @date 2024/12/21
 */
class VelocityTemplateRenderer {

    /**
     * 渲染模板文件
     *
     * @param templatePath 模板文件路径(相对于 classpath)
     * @param context Velocity 上下文
     * @return 渲染结果
     * @throws IllegalArgumentException 模板不存在或语法错误
     */
    fun render(templatePath: String, context: VelocityContext): String {
        ensureInitialized()

        return try {
            val template: Template = Velocity.getTemplate(templatePath)
            val writer = StringWriter()
            template.merge(context, writer)
            writer.toString()
        } catch (e: ResourceNotFoundException) {
            throw IllegalArgumentException(
                "Velocity template not found: $templatePath\n" +
                        "Make sure the file exists in classpath", e
            )
        } catch (e: ParseErrorException) {
            throw IllegalArgumentException(
                "Velocity template syntax error in $templatePath:\n" +
                        "  ${e.message}\n" +
                        "  Line: ${e.lineNumber}, Column: ${e.columnNumber}", e
            )
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to render Velocity template: $templatePath", e
            )
        }
    }

    /**
     * 渲染字符串模板(用于内联模板)
     *
     * @param templateContent 模板内容
     * @param context Velocity 上下文
     * @param templateName 模板名称(用于错误提示)
     * @return 渲染结果
     */
    fun renderString(
        templateContent: String,
        context: VelocityContext,
        templateName: String = "inline-template",
    ): String {
        ensureInitialized()

        return try {
            val writer = StringWriter()
            Velocity.evaluate(context, writer, templateName, templateContent)
            writer.toString()
        } catch (e: ParseErrorException) {
            throw IllegalArgumentException(
                "Velocity template syntax error in $templateName:\n" +
                        "  ${e.message}\n" +
                        "  Line: ${e.lineNumber}, Column: ${e.columnNumber}", e
            )
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to render inline Velocity template: $templateName", e
            )
        }
    }

    /**
     * 确保 Velocity 引擎已初始化
     */
    private fun ensureInitialized() {
        if (!VelocityInitializer.isInitialized()) {
            VelocityInitializer.initVelocity()
        }
    }

    companion object {
        /**
         * 单例渲染器
         */
        val INSTANCE = VelocityTemplateRenderer()
    }
}
