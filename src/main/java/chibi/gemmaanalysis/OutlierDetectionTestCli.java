/*
 * The Gemma project.
 * 
 * Copyright (c) 2006-2012 University of British Columbia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package chibi.gemmaanalysis;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.NumberFormat;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.lang3.time.StopWatch;

import ubic.gemma.analysis.preprocess.OutlierDetails;
import ubic.gemma.analysis.preprocess.OutlierDetectionService;
import ubic.gemma.analysis.preprocess.OutlierDetectionServiceWrapper;
import ubic.gemma.analysis.preprocess.OutlierDetectionServiceWrapperImpl;
import ubic.gemma.analysis.preprocess.OutlierDetectionTestDetails;
import ubic.gemma.apps.ExpressionExperimentManipulatingCLI;
import ubic.gemma.model.expression.bioAssay.BioAssay;
import ubic.gemma.model.expression.experiment.BioAssaySet;
import ubic.gemma.model.expression.experiment.ExperimentalFactor;
import ubic.gemma.model.expression.experiment.ExpressionExperiment;

/**
 * Runs outlier detection methods on given data sets and writes results to file.
 * 
 * @version $Id$
 */
public class OutlierDetectionTestCli extends ExpressionExperimentManipulatingCLI {

    private static final String HEADER = "Experiment\tPlatform\t#Factors\t#Samples\t#Outliers\tDetails";
    private static final String HEADER_REG = "Experiment\tPlatform\t#Factors\t#SigFactors\t#Samples\t#Outliers\tDetails";
    private static final String HEADER_BY_MEDIAN = "Experiment\tPlatform\t#Factors\t#Samples\t#Outliers\tDetails";
    private static final String HEADER_BY_MEDIAN_REG = "Experiment\tPlatform\t#Factors\t#SigFactors\t#Samples\t#Outliers\tDetails";
    private static final String HEADER_COMBINED = "Experiment\tPlatform\t#Factors\t#SigFactors\t#Samples\t#Removed\t#Outliers-basic\t#Outliers-by-median\t#Outliers-total\tDetails";
    private String outputFileName;
    private boolean useRegression = false;
    private boolean findByMedian = false;
    private boolean useCombinedMethod = false;

    /**
     * @param args
     */
    public static void main( String[] args ) {
        OutlierDetectionTestCli testCli = new OutlierDetectionTestCli();
        StopWatch watch = new StopWatch();
        watch.start();
        try {
            Exception ex = testCli.doWork( args );
            if ( ex != null ) {
                ex.printStackTrace();
            }
            watch.stop();
            log.info( "Elapsed time: " + watch.getTime() / 1000 + " seconds" );
        } catch ( Exception e ) {
            throw new RuntimeException( e );
        }
        System.exit( 0 );
    }

    @Override
    public String getShortDesc() {
        return "Run outlier detection tool on given data set(s)";
    }

    @Override
    @SuppressWarnings("static-access")
    protected void buildOptions() {
        super.buildOptions();
        Option outputFileOption = OptionBuilder.hasArg().withArgName( "filename" )
                .withDescription( "Name and path of output file." ).withLongOpt( "output" ).create( 'o' );

        addOption( outputFileOption );

        Option regressionOption = OptionBuilder.withArgName( "regression" )
                .withDescription( "Regress out significant experimental factors before detecting outliers." )
                .withLongOpt( "regression" ).create( 'r' );

        addOption( regressionOption );

        Option findByMedianOption = OptionBuilder
                .withArgName( "findByMedian" )
                .withDescription(
                        "Find outliers by comparing first, second, or third quartiles of sample correlations." )
                .withLongOpt( "findByMedian" ).create( 'm' );

        addOption( findByMedianOption );

        Option combinedMethodOption = OptionBuilder.withArgName( "combinedMethod" )
                .withDescription( "Combine results from two outlier detection methods." ).withLongOpt( "combined" )
                .create( 'c' );

        addOption( combinedMethodOption );
    }

    @Override
    protected void processOptions() {
        super.processOptions();
        if ( !hasOption( "output" ) ) {
            System.out.println( "Output file name is required." );
            throw new RuntimeException();
        }
        this.outputFileName = getOptionValue( "output" );

        this.useRegression = hasOption( "regression" );
        this.useCombinedMethod = hasOption( "combined" );
        this.findByMedian = hasOption( "findByMedian" );

    }

    @Override
    protected Exception doWork( String[] args ) {
        Exception err = processCommandLine( "Outlier Detection Test", args );

        if ( err != null ) {
            return err;
        }

        File file = new File( outputFileName );
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits( 4 );

        try {

            file.createNewFile();

            FileWriter fw = new FileWriter( file.getAbsoluteFile() );
            BufferedWriter bw = new BufferedWriter( fw );

            if ( useCombinedMethod )
                bw.write( HEADER_COMBINED );
            else if ( findByMedian && useRegression )
                bw.write( HEADER_BY_MEDIAN_REG );
            else if ( findByMedian )
                bw.write( HEADER_BY_MEDIAN );
            else if ( useRegression ) {
                bw.write( HEADER_REG );
            } else {
                bw.write( HEADER );
            }

            bw.newLine();

            for ( BioAssaySet bas : expressionExperiments ) {

                if ( !( bas instanceof ExpressionExperiment ) ) continue;

                ExpressionExperiment ee = ( ExpressionExperiment ) bas;

                try {

                    OutlierDetectionTestDetails testDetails = findOutliers( ee );

                    if ( useCombinedMethod ) {
                        writeResultsToFileCombined( bw, file, nf, ee, testDetails );
                    } else if ( findByMedian ) {
                        writeResultsToFileByMedian( bw, file, nf, ee, testDetails );
                    } else {
                        writeResultsToFileBasic( bw, file, nf, ee, testDetails );
                    }

                    bw.flush();

                } catch ( Exception e ) {
                    log.error( "Error while processing " + ee + ": " + e.getMessage() );
                    errorObjects.add( ee + ": " + e.getMessage() );
                    continue;
                }

                successObjects.add( ee.toString() );
            }

            bw.close();

        } catch ( Exception e ) {
            log.error( "Caught exception: " + e );
        } finally {
            summarizeProcessing();
        }

        return null;
    }

    /*** Call the relevant outlier detection method ***/
    private OutlierDetectionTestDetails findOutliers( ExpressionExperiment ee ) {

        log.info( "Processing experiment " + ee.getShortName() );

        OutlierDetectionService outlierDetector = this.getBean( OutlierDetectionService.class );
        OutlierDetectionServiceWrapper outlierWrapper = new OutlierDetectionServiceWrapperImpl();

        if ( useCombinedMethod ) {
            log.info( "Will combine results from two methods..." );
            return outlierWrapper.findOutliersByCombinedMethod( ee, outlierDetector );
        }

        return outlierWrapper.findOutliers( ee, outlierDetector, useRegression, findByMedian );

    }

    /*** Write results to the output file; file name must be given as argument ***/
    private void writeResultsToFileBasic( BufferedWriter bw, File file, NumberFormat nf, ExpressionExperiment ee,
            OutlierDetectionTestDetails testDetails ) {

        try {
            // Get information about the experiment:
            ee = this.eeService.thawLite( ee );
            System.out.println( "Writing results to file for " + ee.getShortName() );
            bw.write( ee.getShortName() );
            bw.write( "\t" + getPlatforms( ee ) );
            bw.write( "\t" + testDetails.getNumExpFactors() );
            if ( useRegression ) {
                bw.write( "\t" + testDetails.getNumSigFactors() );
            }
            bw.write( "\t" + ee.getBioAssays().size() );
            bw.write( "\t" + testDetails.getNumOutliers() );
            // Get information about each of the outliers:
            for ( OutlierDetails outlier : testDetails.getOutliers() ) {
                bw.write( "\t" + outlier.getBioAssay().getName() );
                bw.write( "\t" + nf.format( outlier.getThresholdCorrelation() ) );
                bw.write( "\t" + nf.format( outlier.getScore() ) );
            }
            bw.write( "\tlast threshold: " + nf.format( testDetails.getLastThreshold() ) );
            if ( useRegression ) {
                for ( ExperimentalFactor factor : testDetails.getSignificantFactors() ) {
                    bw.write( "\t" + factor.getName() );
                }
            }
            bw.newLine();

        } catch ( IOException e ) {
            throw new RuntimeException( e );
        }

    }

    /*** Write results to the output file; file name must be given as argument ***/
    private void writeResultsToFileByMedian( BufferedWriter bw, File file, NumberFormat nf, ExpressionExperiment ee,
            OutlierDetectionTestDetails testDetails ) {

        try {
            // Get information about the experiment:
            ee = this.eeService.thawLite( ee );
            System.out.println( "Writing results to file for " + ee.getShortName() );
            bw.write( ee.getShortName() );
            bw.write( "\t" + getPlatforms( ee ) );
            bw.write( "\t" + testDetails.getNumExpFactors() );
            if ( useRegression ) {
                bw.write( "\t" + testDetails.getNumSigFactors() );
            }
            bw.write( "\t" + ee.getBioAssays().size() );
            bw.write( "\t" + testDetails.getNumOutliers() );
            // Get information about each of the outliers (should be in sorted order since outliers is a sorted list):
            for ( OutlierDetails outlier : testDetails.getOutliers() ) {
                bw.write( "\t" + outlier.getBioAssay().getName() );
                bw.write( "\t" + nf.format( outlier.getFirstQuartile() ) + "/"
                        + nf.format( outlier.getMedianCorrelation() ) + "/" + nf.format( outlier.getThirdQuartile() ) );
            }
            if ( useRegression ) {
                for ( ExperimentalFactor factor : testDetails.getSignificantFactors() ) {
                    bw.write( "\t" + factor.getName() );
                }
            }
            bw.newLine();

        } catch ( IOException e ) {
            throw new RuntimeException( e );
        }

    }

    /*** Write results to the output file; file name must be given as argument ***/
    private void writeResultsToFileCombined( BufferedWriter bw, File file, NumberFormat nf, ExpressionExperiment ee,
            OutlierDetectionTestDetails testDetails ) {

        try {
            // Get information about the experiment:
            ee = this.eeService.thawLite( ee );
            System.out.println( "Writing results to file for " + ee.getShortName() );
            bw.write( ee.getShortName() );
            bw.write( "\t" + getPlatforms( ee ) );
            bw.write( "\t" + testDetails.getNumExpFactors() );
            if ( useRegression ) {
                bw.write( "\t" + testDetails.getNumSigFactors() );
            }
            bw.write( "\t" + ee.getBioAssays().size() );
            bw.write( "\t" + testDetails.getNumRemoved() );
            bw.write( "\t" + testDetails.getNumOutliersBasicAlgorithm() );
            bw.write( "\t" + testDetails.getNumOutliersByMedian() );
            bw.write( "\t" + testDetails.getNumOutliers() );
            // Get information about each of the outliers
            for ( OutlierDetails outlier : testDetails.getOutliers() ) {
                bw.write( "\t" + outlier.getBioAssay().getName() );
            }
            if ( useRegression ) {
                for ( ExperimentalFactor factor : testDetails.getSignificantFactors() ) {
                    bw.write( "\t" + factor.getName() );
                }
            }
            bw.newLine();

        } catch ( IOException e ) {
            throw new RuntimeException( e );
        }
    }

    /*** Return names of platforms used in the experiment as string, separated by "/" ***/
    private String getPlatforms( ExpressionExperiment ee ) {

        StringBuilder buffer = new StringBuilder( 32 );
        String platforms;

        for ( BioAssay bioAssay : ee.getBioAssays() ) {
            if ( buffer.indexOf( bioAssay.getArrayDesignUsed().getShortName() ) == -1 ) {
                buffer.append( bioAssay.getArrayDesignUsed().getShortName() + "/" );
                buffer.ensureCapacity( 10 );
            }
        }

        platforms = buffer.substring( 0, buffer.length() - 1 );

        return platforms;
    }

}