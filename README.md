Graxxia
=======

Graxxia is a library adds data analysis features to Groovy, similar to languages such as
R, Julia, etc, and also to libraries such as Pandas for Python.

It should be emphasised that there is nearly nothing in Graxxia itself that is
not just a wrapper around other existing libraries. For example Matrix support
all comes from Apache commons-math, parsing of CSV and TSV (tab separated) files
comes from OpenCSV and GroovyCSV, etc.

What can you do with Graxxia? You can get just a few interesting things:

  *  An approximate equivalent to data frames in R
  *  Read and write tab separated data files
  *  Simple statistics computations
  *  A prototype (very alpha) imitation of the linear modelling expression syntax in R (y ~ x + z, etc)

Obviously this is not even remotely comparable to what is available for data
analysis in the other languages mentioned above. However these classes can
make doing simple data analysis in Groovy quite powerful and in some cases
compares very favorably to doing it in other languages. Combined with the
Groovy Shell, it becomes quite useful for interactive data analysis.


Quick Try Out
============

Graxxia is now available from Maven Central! That means you can use it without any download or
compile via the @Grab annotation. To give Graxxia a try, start a Groovy shell:

    groovysh

Then enter the following:

    groovy:000> @Grab('org.graxxia:graxxia:0.9')
    groovy:001> import graxxia.*
    groovy:001> m = new Matrix([[3,4,5],[6,2,3]])
    ===> 2x3 Matrix:
    0:    3          4          5 
    1:    6          2          3

The @Grab line may take a while depending on whether you already downloaded
any of Graxxia's dependencies or not. It will only be slow the first time you
run it.

Building and Using It from Source
=================================

It's pretty straight forward:

Check out the code:

 git clone https://github.com/ssadedin/graxxia.git


Build it:

    cd graxxia
    ./gradlew jar

Put it in your classpath. A nice way to play around with it is with groovysh. So, for example:

    groovysh -cp build/libs/graxxia.jar 


Note: this build process builds a fully self-contained jar file that contains
all dependencies (sometimes called a 'fat' jar). In rare situations these could 
conflict with other libraries already in your class path.

Note: if you encounter problems, please check that your Groovy version matches
that specified in the build.gradle file. Graxxia should work with versions of
Groovy > 2.2, but problems can occur if your version doesn't match (this can
happen even between point releases of the same major version, unforutnately).

Examples
==========

The heart of Graxxia is the Matrix class. Start by importing the Graxxia classes:

    groovy:000> import graxxia.*

Now make a simple Matrix:

    groovy:000> m = [[2,3,4],[8,6,2],[10,4,3]] as Matrix
    ===> 3x3 Matrix:
    0:	2.0,	3.0,	4.0
    1:	8.0,	6.0,	2.0
    2:	10.0,	4.0,	3.0

So straight away you can see, there are some conversions between arrays, and
lists of doubles that are built in for you. In general you can just take any 2
dimensional array / list combination and turn it into a Matrix. In fact, all
Graxxia cares about is that they are Iterable, so you can even feed a Matrix
dynamically if you like.

We can access the elements, as you would expect, via indexing with row and column respectively:

    groovy:000> m[2][1]
    ===> 4.0

An important thing to point out here is that everything is using zero-based indexing, not 1-based.

A whole row is accessed by omitting the second index:

    groovy:000> m[2] as String
    ===> [10.0, 4.0, 3.0]

A whole column is accessed by omitting the first index:

    groovy:000> m[][1]
    ===> [3.0,6.0,4.0]

Graxxia's Matrix class returns a special MatrixColumn object for column access. You can treat it as a List<Double>, but it is actually working under the covers to reflect accessors back into the original Matrix from which the column came. That is, you are not looking at a copy of the data, you are looking at the actual data. No copy is made when you access by column, even thought the data is stored natively in row format.

Like R's data frames, Graxxia lets you give columns names:

    groovy:000> m.@names = ["x","y","z"]
    ===> [x, y, z]
  
We can now see these when we display the matrix:

    groovy:000> m
    ===> 3x3 Matrix:
        x	    y	    z
    0:	2.0,	3.0,	4.0
    1:	8.0,	6.0,	2.0
    2:	10.0,	4.0,	3.0

Matrices are Expandos, a feature which allows you to add non-numeric columns to a Matrix:

    groovy:000> m.animal = ["cat","dog","horse"]
    ===> [cat, dog, horse]
    groovy:000> m
    ===> 3x3 Matrix:
          animal	x	    y	    z
    0:	  cat     2.0,	3.0,	4.0
    1:	  dog     8.0,	6.0,	2.0
    2:	  horse   10.0,	4.0,	3.0

Matrices are Iterable objects, so they get all the normal Groovy magic methods that are available on Iterators. For example, we can 'grep' a Matrix in a useful way:

    groovy:000> m.grep { x > 2 }.animal
    ===> [dog, horse]  

You can load and save matrices and they remember their column names:

    m.save("test.tsv")
    n = Matrix.load("test.tsv")

Of course, matrix algebra itself works and all the scalar and matrix operators do what you (hopefully) expect:

    groovy:000> m = m + m
    ===> 3x3 Matrix:
    0:    4,	6,	8
    1:    16,	12,	4
    2:    20,	8,	6
    groovy:000> m = m * 2
    ===> 3x3 Matrix:
    0:    8,	12,	16
    1:    32,	24,	8
    2:    40,	16,	12

The Stats class is a wrapper for the Commons-Math DescriptiveStatistics class. It will pull anything from an Iterable and give you back statistics about it, including rows and columns from a Matrix:

    m.collect { Stats.from(it).mean }




