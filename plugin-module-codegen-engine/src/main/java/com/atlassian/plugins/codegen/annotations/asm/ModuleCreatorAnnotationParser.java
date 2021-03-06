package com.atlassian.plugins.codegen.annotations.asm;

import java.io.InputStream;
import java.util.Map;

import com.atlassian.plugins.codegen.annotations.BambooPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.BitbucketPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.ConfluencePluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.CrowdPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.FeCruPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.JiraPluginModuleCreator;
import com.atlassian.plugins.codegen.annotations.RefAppPluginModuleCreator;
import com.atlassian.plugins.codegen.modules.PluginModuleCreator;
import com.atlassian.plugins.codegen.modules.PluginModuleCreatorRegistry;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @since 3.6
 */
public class ModuleCreatorAnnotationParser extends AbstractAnnotationParser
{
    public static final String MODULE_PACKAGE = "com.atlassian.plugins.codegen.modules";

    private static final Map<String, String> annotationProductMap = ImmutableMap.<String, String>builder()
            .put(BambooPluginModuleCreator.class.getName(), PluginModuleCreatorRegistry.BAMBOO)
            .put(BitbucketPluginModuleCreator.class.getName(), PluginModuleCreatorRegistry.BITBUCKET)
            .put(ConfluencePluginModuleCreator.class.getName(), PluginModuleCreatorRegistry.CONFLUENCE)
            .put(CrowdPluginModuleCreator.class.getName(), PluginModuleCreatorRegistry.CROWD)
            .put(FeCruPluginModuleCreator.class.getName(), PluginModuleCreatorRegistry.FECRU)
            .put(JiraPluginModuleCreator.class.getName(), PluginModuleCreatorRegistry.JIRA)
            .put(RefAppPluginModuleCreator.class.getName(), PluginModuleCreatorRegistry.REFAPP)
            .build();

    private final PluginModuleCreatorRegistry pluginModuleCreatorRegistry;

    public ModuleCreatorAnnotationParser(PluginModuleCreatorRegistry pluginModuleCreatorRegistry)
    {
        this.pluginModuleCreatorRegistry = pluginModuleCreatorRegistry;
    }

    public void parse() throws Exception
    {
        ClassLoader oldLoader = Thread.currentThread()
                .getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(getClass().getClassLoader());
        parse(MODULE_PACKAGE, new ModuleClassVisitor());
        Thread.currentThread()
                .setContextClassLoader(oldLoader);
    }

    public void parse(String basePackage) throws Exception
    {
        ClassLoader oldLoader = Thread.currentThread()
                .getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(getClass().getClassLoader());
        parse(basePackage, new ModuleClassVisitor());
        Thread.currentThread()
                .setContextClassLoader(oldLoader);
    }

    public class ModuleClassVisitor extends ClassVisitor
    {

        private String visitedClassname;
        private boolean isModuleCreator;

        public ModuleClassVisitor()
        {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces)
        {
            this.visitedClassname = normalize(name);
            String iface = PluginModuleCreator.class.getName()
                    .replace('.', '/');
            this.isModuleCreator = ArrayUtils.contains(interfaces, iface);
            if (!isModuleCreator)
            {
                this.isModuleCreator = superHasInterface(superName, iface);
            }
        }

        private boolean superHasInterface(String superName, String interfaceName)
        {
            boolean hasInterface = false;

            if (normalize(superName).equals("java.lang.Object"))
            {
                return false;
            }

            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = superName.replace('.', '/');

            try (InputStream is = classLoader.getResourceAsStream(path + ".class"))
            {
                if (is != null)
                {
                    ClassReader classReader = new ClassReader(is);
                    hasInterface = ArrayUtils.contains(classReader.getInterfaces(), interfaceName);
                    if (!hasInterface)
                    {
                        hasInterface = superHasInterface(classReader.getSuperName(), interfaceName);
                    }
                }
            }
            catch (Exception ignored)
            {
                //don't care
            }

            return hasInterface;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String annotationName, boolean isVisible)
        {
            String normalizedName = normalize(annotationName);

            if (isModuleCreator && annotationProductMap.containsKey(normalizedName))
            {
                return new ProductCreatorAnnotationVisitor(normalizedName);
            }

            return null;
        }


        @Override
        public MethodVisitor visitMethod(int i, String s, String s1, String s2, String[] strings)
        {
            return null;
        }

        @Override
        public FieldVisitor visitField(int i, String s, String s1, String s2, Object o)
        {
            return null;
        }

        private class ProductCreatorAnnotationVisitor extends AnnotationVisitor
        {

            private String annotationName;

            private ProductCreatorAnnotationVisitor(final String annotationName)
            {
                super(Opcodes.ASM5);
                this.annotationName = annotationName;
            }

            @Override
            public void visitEnd()
            {

                super.visitEnd();

                String productId = annotationProductMap.get(annotationName);
                if (StringUtils.isNotBlank(productId))
                {
                    try
                    {
                        PluginModuleCreator creator = (PluginModuleCreator) Class.forName(visitedClassname)
                                .newInstance();
                        pluginModuleCreatorRegistry.registerModuleCreator(productId, creator);
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                        //just don't register
                    }
                }
            }
        }
    }
}
