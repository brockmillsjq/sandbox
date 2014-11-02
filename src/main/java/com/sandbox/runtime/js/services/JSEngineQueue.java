package com.sandbox.runtime.js.services;

import com.sandbox.runtime.js.models.Console;
import com.sandbox.runtime.js.utils.FileUtils;
import com.sandbox.runtime.js.utils.NashornRuntimeUtils;
import com.sandbox.runtime.models.SandboxScriptEngine;
import com.sandbox.runtime.utils.GenericEngineQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

/**
 * Created by nickhoughton on 8/08/2014.
 */
public class JSEngineQueue extends GenericEngineQueue {

    static Logger logger = LoggerFactory.getLogger(JSEngineQueue.class);

    public JSEngineQueue(int targetInQueue, ApplicationContext context) {
        super(context, targetInQueue);
    }

    //executed once when the engine is created, load in a seal all the libs
    @Override
    protected SandboxScriptEngine initializeEngine(SandboxScriptEngine sandboxEngine){
        logger.debug("Initializing engine..");

        try {
            //apply generic configuration before queueing
            ScriptEngine engine = sandboxEngine.getEngine();

            Bindings globalScope = engine.getContext().getBindings(ScriptContext.GLOBAL_SCOPE);
            if(globalScope == null) {
                engine.getContext().setBindings(new SimpleBindings(), ScriptContext.GLOBAL_SCOPE);
                globalScope = engine.getContext().getBindings(ScriptContext.GLOBAL_SCOPE);
            }

            loadAndSealScript("lodash-2.4.1.js","lib/lodash-2.4.1.min", "_", globalScope, engine);
            loadAndSealScript("moment.js","lib/moment-2.8.2.min", "moment", globalScope, engine);
            loadAndSealScript("validator.js","lib/validator.min", "validator", globalScope, engine);
            loadAndSealScript("sandbox-validator.js","sandbox-validator", "sandboxValidator", globalScope, engine);

            engine.eval("quit = undefined",globalScope);

        } catch (ScriptException e) {
            logger.error("Error configuring script engine",e);
        }

        return sandboxEngine;
    }

    private void loadAndSealScript(String name, String file, String objectName, Bindings globalScope, ScriptEngine engine) throws ScriptException {
        globalScope.put(ScriptEngine.FILENAME, name);
        engine.eval(FileUtils.loadJSFromResource(file), globalScope);
        engine.eval("Object.freeze(" + objectName + "); Object.seal(" + objectName + ");", globalScope);
        globalScope.put(objectName, engine.eval(objectName, globalScope));
    }

    //this is the executed per request, so everytime the engine goes back into the queue this runs to clear any junk the user might have left
    @Override
    protected SandboxScriptEngine prepareEngine(SandboxScriptEngine sandboxEngine){
        Console consoleInstance = context.getBean(Console.class);
        sandboxEngine.setConsole(consoleInstance);

        NashornRuntimeUtils nashornRuntimeUtils = (NashornRuntimeUtils) context.getBean("nashornUtils","temporary");

        final Bindings globalScope = sandboxEngine.getEngine().getContext().getBindings(ScriptContext.GLOBAL_SCOPE);
        final Bindings engineScope = new SimpleBindings();
        final ScriptContext ctx = new SimpleScriptContext();
        ctx.setBindings(globalScope, ScriptContext.GLOBAL_SCOPE);
        ctx.setBindings(engineScope, ScriptContext.ENGINE_SCOPE);
        ctx.setAttribute("_console", sandboxEngine.getConsole(),ScriptContext.ENGINE_SCOPE);

        ctx.setAttribute("nashornUtils", nashornRuntimeUtils,ScriptContext.ENGINE_SCOPE);

        // monkey patch nashorn
        try {
            globalScope.put(ScriptEngine.FILENAME, "<sandbox-internal>");
            sandboxEngine.getEngine().eval(FileUtils.loadJSFromResource("sandbox-patch"), ctx);
        } catch (ScriptException e) {
            logger.error("Error postProcessing engine",e);
        }

        sandboxEngine.setContext(ctx);

        return sandboxEngine;
    }

    @Override
    protected SandboxScriptEngine postProcessEngine(SandboxScriptEngine sandboxEngine) {

        return sandboxEngine;
    }

}