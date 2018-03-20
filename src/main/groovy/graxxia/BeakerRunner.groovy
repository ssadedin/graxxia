package graxxia

import org.codehaus.groovy.runtime.StackTraceUtils;

import groovy.json.JsonSlurper
import groovy.time.TimeCategory

import javax.script.Bindings
import javax.script.ScriptContext;
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

class BeakerInterface extends HashMap {
    
    BeakerRunner runner
    
    BeakerInterface(BeakerRunner runner) {
        this.runner = runner
    }
    
    int foo() {
       println "FOO"
       return 23 
    }
    
    void loadLibrary(List<String> libs) {
        for(String lib in libs) {
            runner.loadLibrary(lib)
        }
    }
}

class BeakerRunner {
    
//    def log  = org.slf4j.LoggerFactory.getLogger("BeakerRunner")
    
    
    boolean verbose = false
    
    def log = [
            info: { if(verbose) println "INFO\t" + it },
            error: { System.err.println("ERROR:\t" + it) }
        ]
    
    
    String beakerPath
    
    boolean includeBeakerImports = true
    
    GroovyShell shell
    
    Map<String,ScriptEngine> engines = [:]
    
    Map<String,String> engineInits = [
        "JavaScript" : """
        """
    ]

    Binding binding = new Binding()
    
    boolean errors
    
    String errorScript
    
    Map<String,Object> params
    
    Map scriptBinding = [:]
    
    boolean initialized = false
    
    BeakerRunner(Map<String,Object> params = [:], String notebookPath) {
        this.beakerPath = notebookPath
        this.params = params
        
        scriptBinding.put("beaker", new BeakerInterface(this))
        scriptBinding.put("baseDir", new File(".").absolutePath)
    }
    
    void init(Map params) {
        if(initialized)
            return 
            
        binding.setVariable("baseDir",new File(".").absolutePath)
        binding.setVariable("beaker",this.scriptBinding.beaker)
        
        initParameters()
        
        shell = new GroovyShell(binding)        
        
        initialized = true
    }
    
    void initParameters() {
        params.each { k,v ->
            
            def value = String.valueOf(v)
            if(value.isInteger()) {
                value = value.toInteger()
            }
            else 
            if(value.isFloat()) {
                value = value.toFloat()
            }
            else 
            if(value == "true") {
                value = Boolean.TRUE
            }
            else 
            if(value == "false") {
                value = Boolean.FALSE
            }
            binding.setVariable(k,value)
            scriptBinding.put(k,value)
        }
    }
    
    Object run() {
        init()
        
        List<Map> contents = extract()
        
        def result = runChunks(contents)

        return result
    }
    
    Object runChunks(List<Map> chunks) {
               
        Object result
        int n = 0
        Date sectionStartTime
        for(Map codeblock in chunks) {
            if(codeblock.type == "groovy") {
                log.info "Executing chunk $n starting with " + codeblock.code.readLines()[0]
                result = this.groovyShellEvaluate(importCode + '\n' + codeblock.code)
                
                if(errors)
                    return
                
                // If the block starts with a comment indicating it has
                // overrideable parameters, run the init to let the command line
                // set them
                if(codeblock.code.startsWith('// Parameters')) {
                    log.info "Setting ${this.params.size()} command line parameters after parameter section"
                    initParameters()
                }
                ++n
            }
            else
            if(codeblock.type == "section") {
                
                Date nowTime = new Date()
                if(sectionStartTime!=null) {
                    println("\nExecuted in " + TimeCategory.minus(nowTime,sectionStartTime) + "\n") 
                }
                sectionStartTime = nowTime
                
                println "=" * 80
                println " $codeblock.code ".center(80," ")
                println "=" * 80
            }
            else
            if(engines.containsKey(codeblock.type)) {
                ScriptEngine engine = engines[codeblock.type]
                result = engine.eval(codeblock.code)
            }
            else {
                ScriptEngine engine = new ScriptEngineManager().getEngineByName(codeblock.type)
                if(engine) {
                    log.info "Initializing $codeblock.type engine ..."
                    
                    Bindings bindings = engine.getBindings(ScriptContext.GLOBAL_SCOPE)
                    scriptBinding.each { 
                        bindings.put(it.key, it.value)
                    }
                    
                    engine.eval(engineInits.get(codeblock.type, ""))
                    engines.put(codeblock.type, engine)
                    result = engine.eval(codeblock.code)
                }
                else {
                    log.info "Skip cell of type $codeblock.type"
                }
            }
        } 
        return result
    }
    
    String importCode
    
    List extract() {
        extract(beakerPath)
    }
    
    List extract(String path) {
        
        File beakerFile = new File(path).absoluteFile
        if(!beakerFile.exists())
            throw new FileNotFoundException("Beaker file could not be found at location ${beakerFile.absolutePath}")
            
        log.info("Opening beaker file at $beakerFile.absolutePath")
        def codeChunks = beakerFile.newReader().withReader { r ->
            def bkr = new JsonSlurper().parse(r)

            def groovyImports = bkr.evaluators.grep { it.name == "Groovy" }
                                              .imports[0].split("\n")
                                              .grep { includeBeakerImports || !it.startsWith("com.twosigma") }
                                              
            importCode = groovyImports.collect { "import " + it + ";" }.join("\n")                                               
            log.info "Default imports are: \n" + importCode
            boolean firstGroovy = true
            [ 
               [
                   type: "groovy",
                   code:  importCode
               ]
            ] + bkr.cells.collect { cell ->

                def cellType =(cell.evaluator?:cell.type).toLowerCase()
                
                String cellCode = ""
                switch(cellType) {
                    case "markdown": 
                        cellCode = cell.body.join("\n")
                        break
                    case "section":
                        cellCode = cell.title
                        break
                    default:
                        cellCode = cell.input?.body?.join("\n")
                        break
                }   
                
                [ type: cellType, code: cellCode, initialization: cell.initialization?:false]
            }
        }

        log.info "Found ${codeChunks.size()} code chunks in beaker notebook"

        return codeChunks        
    }

    Object groovyShellEvaluate(String script) {
        try {
            return this.shell.evaluate(script)
        }
        catch(Exception e) {
            log.error(" ERROR ".center(100, "="))
            log.error("Script:\n")
            log.error("    " + script.replaceAll("\\n","\n    "))
            log.error("\nExperienced error:\n")
            def s = StackTraceUtils.sanitizeRootCause(e)
            s.printStackTrace()
            log.error("=" * 100)
            errors = true
            if(errorScript == null)
                errorScript = script

            return "ERROR: " + e.toString()
        }
    }
    
    void loadLibrary(String lib) {
        
        log.info "Loading library: $lib"
        
        List<Map> initCells = extract(lib).grep { it.initialization }
        
        log.info "Library has ${initCells.size()} init cells"
      
        runChunks(initCells)  
    }
    
    static void main(String [] args) {
        
        CliBuilder cli = new CliBuilder()
        cli.with {
            p 'Pass parameter value in form <name>=<value>', args: org.apache.commons.cli.Option.UNLIMITED_VALUES
            v 'Verbose'
        }
        
        def opts = cli.parse(args)
        if(!opts)
            System.exit(1)
        
        if(opts.arguments().isEmpty()) {
            System.err.println "Please provide the notebook to run as an argument"
            System.exit(1)
        }
            
        Map params = opts.ps ? opts.ps.collectEntries { p -> 
            println "Parsing parameter: " + p
            int n = p.indexOf('='); 
            [ p.substring(0,n), p.substring(n)] 
        } : [:]
        
        
        String notebook
        for(arg in opts.arguments()) {
            if(arg.contains('=')) {
                params[arg.tokenize('=')[0]] = arg.tokenize('=')[1]
            }
            else {
                notebook = arg
            }
        }
        
        println "="*100
        println "Beaker Runner"
        println "="*100
        
        if(params) {
            println "\nRunning with parameters: " + params.collect { k,v -> "$k : $v" }.join('\n') + '\n'
        }
        
        if(!opts.arguments()) {
            cli.usage()
            println "\nPlease specify a notebook to run as last argument\n"
            System.exit(1)
        }
        

        BeakerRunner br = new BeakerRunner(params,notebook)
        if(opts.v)
            br.verbose = true
            
        br.run()
        
        if(br.errors) {
            println "\nTerminated due to errors: please see above for messages to explain what went wrong\n"
            System.exit(1)
        }
    }

}
