package com.tambapps.android.grooidexample;

import android.util.Log;

import com.android.dx.Version;
import com.android.dx.cf.direct.DirectClassFile;
import com.android.dx.cf.direct.StdAttributeFactory;
import com.android.dx.command.dexer.DxContext;
import com.android.dx.dex.DexOptions;
import com.android.dx.dex.cf.CfOptions;
import com.android.dx.dex.cf.CfTranslator;
import com.android.dx.dex.code.PositionList;
import com.android.dx.dex.file.ClassDefItem;
import com.android.dx.dex.file.DexFile;

import org.codehaus.groovy.control.BytecodeProcessor;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import dalvik.system.DexClassLoader;
import groovy.lang.Binding;
import groovy.lang.GrooidClassLoader;
import groovy.lang.Script;

/**
 * A shell capable of executing Groovy scripts at runtime, on an Android device.
 *
 * https://github.com/melix/grooidshell-example/blob/master/GroovyDroid/src/main/java/me/champeau/groovydroid/GrooidShell.java
 *
 * @author Cédric Champeau
 */
public class GrooidShell extends Binding {

    private static final String DEX_IN_JAR_NAME = "classes.dex";
    private static final Attributes.Name CREATED_BY = new Attributes.Name("Created-By");

    private final DexOptions dexOptions;
    private final CfOptions cfOptions;
    private final DxContext dxContext = new DxContext();

    private final File tmpDynamicFiles;
    private final ClassLoader classLoader;

    public GrooidShell(File tmpDir, ClassLoader parent) {
        tmpDynamicFiles = tmpDir;
        classLoader = parent;
        dexOptions = new DexOptions();
        dexOptions.minSdkVersion = 26;
        cfOptions = new CfOptions();
        cfOptions.positionInfo = PositionList.LINES;
        cfOptions.localInfo = true;
        cfOptions.strictNameCheck = true;
        cfOptions.optimize = false;
        cfOptions.optimizeListFile = null;
        cfOptions.dontOptimizeListFile = null;
        cfOptions.statistics = false;
    }


    public EvalResult evaluate(String scriptText) throws Exception {
        long sd = System.nanoTime();
        final Set<String> classNames = new LinkedHashSet<String>();
        final DexFile dexFile = new DexFile(dexOptions);
        CompilerConfiguration config = new CompilerConfiguration();
        config.setBytecodePostprocessor(new BytecodeProcessor() {
            @Override
            public byte[] processBytecode(String name, byte[] original) {
                try {
                    DirectClassFile directClassFile = new DirectClassFile(original, name + ".class", false);

                    directClassFile.setAttributeFactory(StdAttributeFactory.THE_ONE);
                    directClassFile.getMagic(); // triggers the actual parsing
                    ClassDefItem classDefItem = CfTranslator.translate(dxContext, directClassFile, original, cfOptions, dexOptions, dexFile);
                    dexFile.add(classDefItem);
                    classNames.add(name);
                } catch (Exception e) {
                    Log.e("GrooidShell", "Couldn't translate class", e);
                }
                return original;
            }
        });

        GrooidClassLoader gcl = new GrooidClassLoader(this.classLoader, config);
        gcl.parseClass(scriptText);

        byte[] dalvikBytecode = dexFile.toDex(new OutputStreamWriter(new ByteArrayOutputStream()), false);


        Map<String, Class> classes = defineDynamic(classNames, dalvikBytecode);
        long compilationTime = System.nanoTime()-sd;
        long execTime = 0;
        Object result = null;
        for (Class scriptClass : classes.values()) {
            if (Script.class.isAssignableFrom(scriptClass)) {
                sd = System.nanoTime();
                Script script = InvokerHelper.createScript(scriptClass, this);
                result = script.run();
                execTime = System.nanoTime()-sd;
                break;
            }
        }
        return new EvalResult(compilationTime, execTime, result);
    }


    private Map<String,Class> defineDynamic(Set<String> classNames, byte[] dalvikBytecode) {
        File tmpDex = new File(tmpDynamicFiles, UUID.randomUUID().toString()+".jar");
        Map<String,Class> result = new LinkedHashMap<String, Class>();
        try {
            FileOutputStream fos = new FileOutputStream(tmpDex);
            JarOutputStream jar = new JarOutputStream(fos, makeManifest());
            JarEntry classes = new JarEntry(DEX_IN_JAR_NAME);
            classes.setSize(dalvikBytecode.length);
            jar.putNextEntry(classes);
            jar.write(dalvikBytecode);
            jar.closeEntry();
            jar.finish();
            jar.flush();
            fos.flush();
            fos.close();
            jar.close();
            DexClassLoader loader = new DexClassLoader(tmpDex.getAbsolutePath(), tmpDynamicFiles.getAbsolutePath(), null, classLoader);
            for (String className : classNames) {
                result.put(className, loader.loadClass(className));
            }
            return result;
        } catch (Throwable e) {
            Log.e("DynamicLoading", "Unable to load class",e);
        } finally {
            tmpDex.delete();
        }
        return null;
    }

    private static Manifest makeManifest() throws IOException {
        Manifest manifest = new Manifest();
        Attributes attribs = manifest.getMainAttributes();
        attribs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attribs.put(CREATED_BY, "dx " + Version.VERSION);
        attribs.putValue("Dex-Location", DEX_IN_JAR_NAME);
        return manifest;
    }

    public static class EvalResult {
        public final long compilationTime;
        public final long execTime;
        public final Object result;

        public EvalResult(long compilationTime, long execTime, Object result) {
            this.compilationTime = compilationTime;
            this.execTime = execTime;
            this.result = result;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Compilation time = ").append(compilationTime / 1000000).append("ms");
            sb.append("\n");
            sb.append("Execution time = ").append(execTime / 1000000).append("ms");
            sb.append("\n");
            sb.append("Result = ").append(result);
            return sb.toString();
        }
    }

}