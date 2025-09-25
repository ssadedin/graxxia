package graxxia

import java.text.ParseException;
import java.text.SimpleDateFormat
import java.util.Date;

import groovy.transform.CompileStatic

/**
 * Defines an interface that can be implemented to allow integration of 
 * user-defined data types into Matrix and TSV / CSV parsing functions
 * 
 * @author ssadedin@gmail.com
 */
@CompileStatic
interface MatrixValueAdapter<T> {
    
    /**
     * Return true if the given value is parseable as this type
     * 
     * @return
     */
    boolean sniff(Object value) 
    
    /**
     * Encode the given object
     * 
     * @param obj
     * @return  String value of the object, for writing to file
     */
    String serialize(T obj) 
    
    /**
     * Parse the given object.
     * 
     * @return
     */
    T deserialize(Object obj) throws ParseException
}
 
class UTCDateMatrixValueAdapter extends DateMatrixValueAdapter {
    
    UTCDateMatrixValueAdapter() {
        // Create a formatter that understands an ISO8601 timezone indicator ("X")
        this.format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX")
        // Indicate that input is in UTC
        this.format.setTimeZone(TimeZone.getTimeZone("UTC"))
    }
}

@CompileStatic
class NumberMatrixValueAdapter implements MatrixValueAdapter<Number> {

    @Override
    public boolean sniff(Object value) {
        return value instanceof Number
    }
    

    @Override
    public String serialize(Number obj) {
        return String.valueOf(obj);
    }

    @Override
    public Double deserialize(Object obj) throws ParseException {
        return (double) obj;
    }
}

@CompileStatic
class StringMatrixValueAdapter implements MatrixValueAdapter<Object> {

    @Override
    public boolean sniff(Object value) {
        return true
    }

    @Override
    public String serialize(Object obj) {
        return String.valueOf(obj)
    }

    @Override
    public String deserialize(Object obj) throws ParseException {
        return String.valueOf(obj)
    }
}

class DateMatrixValueAdapter implements MatrixValueAdapter<Date> {
    protected SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    
    DateMatrixValueAdapter() {
        // Default to local time zone
        format.setTimeZone(TimeZone.getDefault())
    }
    
    @Override
    public boolean sniff(Object value) {
        if (value instanceof Date)
            return true
        if (!(value instanceof String))
            return false
        try {
            format.parse(String.valueOf(value))
            return true
        } catch (ParseException e) {
            return false
        }
    }
    
    @Override
    public String serialize(Date obj) {
        return format.format(obj)
    }
    
    @Override
    public Date deserialize(Object obj) throws ParseException {
        return format.parse(String.valueOf(obj))
    }
}
