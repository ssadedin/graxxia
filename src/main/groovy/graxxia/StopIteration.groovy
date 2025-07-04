package graxxia

import groovy.transform.CompileStatic


@CompileStatic
@Singleton
class StopIteration {
    
    public static StopIteration getTheInstance() {
        return StopIteration.instance
    }
}
