/*
 *  Graxxia - Groovy Maths Utilities
 *
 *  Copyright (C) 2014 Simon Sadedin, ssadedin<at>gmail.com and contributors.
 *
 *  This file is licensed under the Apache Software License Version 2.0.
 *  For the avoidance of doubt, it may be alternatively licensed under GPLv2.0
 *  and GPLv3.0. Please see LICENSE.txt in your distribution directory for
 *  further details.
 */
package graxxia

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.codehaus.groovy.runtime.NullObject;

import groovy.transform.CompileStatic

class RegressionCategory {
    static RegressionVariable bitwiseNegate(RegressionVariable x)     {
        x.model.predictors = [x]
        return x
    }
    static List<RegressionVariable> bitwiseNegate(List<RegressionVariable> values)     {
        values[0].model.predictors = values
        return values
    }
}

class RegressionVariable {
    
    Regression model 
    
    String name
    
    Closure operator
    
    RegressionVariable bitwiseNegate(RegressionVariable other) {
        model.response = this
        model.predictors  = [other]
        return other
    }
    
    List<RegressionVariable> bitwiseNegate(List<RegressionVariable> variables) {
        model.predictors  = variables
        return variables
    }
    
    List<RegressionVariable> plus(RegressionVariable other) {
        other.operator = { arg1, arg2 -> arg1 + arg2 }
        [this, other]
    }
    
    String toString() {
        name
    }
}

/**
 * A simple class supporting a mini DSL to make linear regression more natural 
 * <p>
 * Example:
 * <code>
 * Matrix m = new Matrix(cow: [1,2,3,4], tree: [5,6,7,8])
 * r = new Regression().model {
 *   grass ~ cow + tree
 * }
 * response = [10,11,12,13]
 * r.run(response, m)
 * println("Predicted value of 11.5 is" + r.predict([11.5]))
 * </code>
 * @author Simon Sadedin
 */
class Regression {
    
    RegressionVariable response
    
    List<RegressionVariable> predictors
    
    @Delegate
    OLSMultipleLinearRegression model = new OLSMultipleLinearRegression()
    
    Matrix beta
    
    double offset

    public Regression() {
    }
    
    Regression model(Closure c) {
        c.delegate = this
        c.resolveStrategy = Closure.DELEGATE_FIRST
        use(RegressionCategory) {
            c()
        }
        
        return this
    }
    
    def propertyMissing(String name) {
        new RegressionVariable(name:name, model: this)
    }
    
    def methodMissing(String name, args) {
        this.response = new RegressionVariable(name:name)
        this.predictors  = args[0] instanceof List ? args.flatten() : [args[0]]
    }
    
    Regression run(def response, Matrix data) {
        def columns = data.getColumns(predictors.collect {it .name})
        def predictorData = new Matrix(columns)
        model.newSampleData(response as double[], predictorData.dataRef)
        
        double [] coeffs = model.estimateRegressionParameters()
        
        this.beta = new Matrix([coeffs[1..-1]])
        this.offset = coeffs[0]
        
        return this
    }
    
    @CompileStatic
    double predict(Iterable<Double> data) {
        double result = offset
        double [] coeffs = beta[0]
        int index = 0
        for(double d in data) {
            result += coeffs[index] * data[index]
            ++index
        }
        return result
    }
    
    @CompileStatic
    double predict(double [] data) {
        double result = offset
        double [] coeffs = beta[0]
        for(int i=0; i<data.length; ++i) {
            result += data[i] * coeffs[i]
        }
        return result
    }
    
    String toString() {
        "Regression of $response.name on ${predictors*.name.join(',')}"
    }
}
