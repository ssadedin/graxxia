package graxxia

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

interface ReaderFactory {
    Reader newReader()
}

class StringReaderFactory implements ReaderFactory {
    String source
    Reader newReader() { FileLike.reader(source) }
}

class FileReaderFactory implements ReaderFactory {
    File file
    Reader newReader() { FileLike.reader(file) }
}

class DirectReaderFactory implements ReaderFactory {
    Reader reader
    Reader newReader() { 
        if(!this.reader) 
            throw new Exception("Reader exhausted : this class was instantiated with a raw reader. Please supply the underlying file to enable re-reading")
        Reader result = this.reader 
        this.reader = null
        return result
    }
}

/**
 * Utility for flexibly working with files to automatically use the right method to
 * open them, eg: sniffing if they are zipped or not.
 * 
 * @author Simon Sadedin
 */
@CompileStatic
class FileLike {
    
    static ReaderFactory readerFactory(Object source) {
        if(source instanceof String)
            return new StringReaderFactory(source:source)
        else
        if(source instanceof File)
            return new FileReaderFactory(file:source)
        else
        if(source instanceof Reader)
            return new DirectReaderFactory(reader:source)
        else
            throw new IllegalArgumentException("No known way to convert object of type ${source.class} to reader")
    }
    
  /**
     * Creates a Reader from any object that can be reasonably interpreted to represent a file,
     * allowing for the possibility that it might be gzipped or bgzipped.
     * <p>
     * If a {@link groovy.lang.Closure} is provided, then the closure will be called, passing the
     * reader as the first parameter. The return value of this function will be the result of 
     * executing the closure. If, however, a Closure is not provided, the return value will be the
     * created {@link java.io.Reader} object to be used.
     * 
     * @param fileLike
     * @param c Optional {@link groovy.lang.Closure} to call back with the created {@link java.io.Reader}
     * @return  result of evaluated closure if provided, or created {@link java.io.Reader}
     */
    @CompileStatic
    static Object reader(fileLike, @ClosureParams(value=SimpleType, options=['java.io.Reader']) Closure c = null) {
        Reader r = createReader(fileLike)
        if(c != null) {
            Object result
            r.withReader {
                result = c(r)
            }
            return result
        }
        else
            return r
    }
    
    static Reader createReader(fileLike) {
        
        if(fileLike instanceof Reader)
            return fileLike
            
        InputStream stream = createStream(fileLike)
        
        return stream.newReader()
    }
    
    /**
     * Convert a String|Path|File|InputStream to an InputStream,
     * caller must close
     * 
     * @param fileLike
     * @return InputStream
     */
    static InputStream createStream(fileLike) {
       
        boolean gzip = false
        boolean bgzip = false
        
        if(fileLike instanceof String) {
            fileLike = new File(fileLike)
        }
        
        if(fileLike instanceof File) {
            fileLike = fileLike.toPath()
        }
        
        if(fileLike instanceof Path) {
            Path path = fileLike
            if(path.toString().endsWith('.gz'))
                gzip = true
            else
            if(path.toString().endsWith('.bgz'))
                bgzip = true
            fileLike = Files.newInputStream(fileLike)
        }
        
        if(!(fileLike instanceof InputStream))
            throw new IllegalArgumentException("Expected object of type String, File, Path or InputStream, but was passed " + fileLike.class.name)
        
        if(gzip) {
            fileLike = new GZIPInputStream((InputStream)fileLike, 128*1024)
        }
        return (InputStream)fileLike
    } 
    
    /**
     * Executes the given closure, passing as parameters all of the given filenames opened as 
     * Writers using intelligent interpretation of the filenames - eg: if ending in .gz, use gzip, etc.
     * <p>
     * Note: if a file name is null, blank or false, a null will be returned for the corresponding writer.
     * 
     * @param fileNames file names to open
     * @param c         Closure to call
     * @return  result of closure
     */
    @CompileDynamic
    static withWriters(List<String> fileNames, Closure c) {
        List<Writer> writers = []
        try {
            for(fileName in fileNames) {
                writers << (fileName ? outputWriter(fileName) : null)
            }
            c(*writers)
        }
        finally {
            List<Exception> errors = writers.findAll { w ->
                closeQuietly(w)
            }
            if(errors) {
                throw errors[0]
            }
        }
    }

    static Exception closeQuietly(Closeable obj) {
        if(obj == null)
            return
        try {
            obj.close()
        }
        catch(Exception e) {
            return e
        }
        return null
    } 
     
    @CompileStatic
    static Writer writer(File file) {
        return outputWriter(file.path)
    }
  
    @CompileStatic
    static Writer writer(String fileName) {
        return outputWriter(fileName)
    }
    
    @CompileStatic
    static Writer outputWriter(String fileName) {
        new PrintWriter(outputStream(fileName))
    } 
    
    @CompileStatic
    static OutputStream outputStream(String fileName, int bufferSize=1048576) {
        
        if(fileName == '-' || fileName == '/dev/stdout')
            return System.out
        
        if(fileName.endsWith(".gz"))
            new GZIPOutputStream(new FileOutputStream(fileName), bufferSize)
        else
            new BufferedOutputStream(new File(fileName).newOutputStream(), bufferSize)
    } 
}
