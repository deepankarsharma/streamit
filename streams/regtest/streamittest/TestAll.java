/**
 * Class which runs all of the test suites
 * $Id: TestAll.java,v 1.6 2002-07-01 21:55:57 aalamb Exp $
 **/
package streamittest;

import junit.framework.*;

public class TestAll extends TestCase {

    public TestAll(String name) {
	super (name);
    }


    /**
     * creates a test suite with all of the tests so far,
     * each of which is run using the specified options.
     **/
    public static Test makeTestSuite(int flags) {
	TestSuite suite = new TestSuite();
	suite.addTest(TestExamples.suite(flags));
	suite.addTest(TestApps.suite(flags));
	suite.addTest(TestFieldInit.suite(flags));
	suite.addTest(TestTemplate.suite(flags));
	return suite;	
    }


    public static Test suite() {
	TestSuite allTests = new TestSuite();

	addUniprocessorTests(allTests);
	addRawTests(allTests);

	return allTests;
    }

    /**
     * add the uniprocessor tests to the test suite framework.
     **/
    public static void addUniprocessorTests(TestSuite allTests) {
	// try with no optimizations running
	allTests.addTest(makeTestSuite(CompilerInterface.NONE));
	
	// try with const prop
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.CONSTPROP));

	// const prop, unrolling, fusion
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.CONSTPROP |
				       CompilerInterface.FUSION));
    }
    
    /**
     * add the raw tests to the test suite framework.
     **/
    public static void addRawTests(TestSuite allTests) {
	// raw with 4 tiles, partition
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.RAW4 |
				       CompilerInterface.PARTITION));

	// raw with 4 tiles, partition, constprop
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.RAW4 |
				       CompilerInterface.PARTITION |
				       CompilerInterface.CONSTPROP));
	// raw 4 with fusion
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.RAW4 |
				       CompilerInterface.FUSION));
	// raw 4 with fusion
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.RAW4 |
				       CompilerInterface.FUSION |
				       CompilerInterface.CONSTPROP));

	// test raw with 8 tiles to see if problem are being introduced by
	// the above optimizations
				       
	// raw with 8 tiles
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.RAW8));

	// raw with 8 tiles, constprop
	allTests.addTest(makeTestSuite(CompilerInterface.NONE |
				       CompilerInterface.RAW8 |
				       CompilerInterface.CONSTPROP));
    }


}
