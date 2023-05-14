package graxxia

import org.slf4j.helpers.Util

import groovy.util.logging.Log
import groovy.util.logging.Slf4j
import py4j.GatewayServer

interface IExec {
    Object exec(String cmd) 
    Object eval(String cmd) 
}

@Slf4j
class Python implements IExec {
    
    StringBuilder output = new StringBuilder()
    
    GatewayServer server
    
    Process pythonProcess
    
    String pythonCommand = 
    """
        gateway = None

        def convert_collections(value):
            
            if type(value) == list:
                return ListConverter().convert(value, gateway._gateway_client)
            
            if type(value) == dict:
                return DictConverter().convert(value, gateway._gateway_client)

            return value

        class Eval(object):
            def exec(self, cmd):
                print(f"Exec:\\n{cmd}\\n")
                return exec(cmd,globals())

            def eval(self, cmd):
                return convert_collections(eval(cmd))

            class Java:
                implements = ["graxxia.IEval"]

        from py4j.java_gateway import JavaGateway, CallbackServerParameters, GatewayParameters
        from py4j.java_collections import *
        exec_obj = Eval()

        print("Starting python server ...")
        gateway = JavaGateway(
            callback_server_parameters=CallbackServerParameters(),
            python_server_entry_point=exec_obj,
            gateway_parameters=GatewayParameters(auto_convert=True)
        )
    """.stripIndent()
    
    
    IExec exec
    
    Python(Object delegate) {
        server = new GatewayServer(delegate)
        
        server.start()
        
        def pyFile = new File('/tmp/graxxia_python.py')
        pyFile.text = pythonCommand
        pythonProcess = "python -u $pyFile.absolutePath".execute()
        pythonProcess.consumeProcessOutput(output, output)
        Thread t = new Thread(this.&pollOutput)
        t.daemon = true
        t.start()
//        log.info("Started python server")
        
        exec = server.getPythonServerEntryPoint([IExec] as Class[])
    }
    
    void pollOutput() {
        while(true) {
            Thread.sleep(1000)
            String newOutput = output.toString()
            output.setLength(0)
            if(newOutput.size()>0)
                System.out.println newOutput
                
            if(!pythonProcess.alive)    
                break
        }
        server.shutdown()
    }

    @Override
    public Object exec(String cmd) {
        return this.exec.exec(cmd)
    }

    @Override
    public Object eval(String cmd) {
        return this.exec.eval(cmd)
    }
    
    static void main(String [] args) {
        log.info "Starting python ..."
        def python = new Python()
        Thread.sleep(5000)
        def result = python.exec("print('Hello world')")

        log.info "Result of call: $result"
        
        log.info "Exiting ..."
        
        python.pythonProcess.destroy()
        
        log.info "Exited."
    }
}
