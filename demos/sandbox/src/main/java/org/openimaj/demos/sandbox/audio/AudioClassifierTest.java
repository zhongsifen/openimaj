/**
 *
 */
package org.openimaj.demos.sandbox.audio;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openimaj.audio.features.MFCCJAudio;
import org.openimaj.audio.reader.OneSecondClipReader;
import org.openimaj.audio.samples.SampleBuffer;
import org.openimaj.data.dataset.GroupedDataset;
import org.openimaj.data.dataset.ListDataset;
import org.openimaj.data.dataset.VFSGroupDataset;
import org.openimaj.experiment.dataset.util.DatasetAdaptors;
import org.openimaj.experiment.evaluation.classification.ClassificationEvaluator;
import org.openimaj.experiment.evaluation.classification.ClassificationResult;
import org.openimaj.experiment.evaluation.classification.analysers.confusionmatrix.CMAggregator;
import org.openimaj.experiment.evaluation.classification.analysers.confusionmatrix.CMAnalyser;
import org.openimaj.experiment.evaluation.classification.analysers.confusionmatrix.CMResult;
import org.openimaj.experiment.validation.ValidationData;
import org.openimaj.experiment.validation.cross.StratifiedGroupedKFold;
import org.openimaj.feature.DoubleFV;
import org.openimaj.feature.FeatureExtractor;
import org.openimaj.ml.annotation.AnnotatedObject;
import org.openimaj.ml.annotation.svm.SVMAnnotator;

/**
 *
 *
 *	@author David Dupplaw (dpd@ecs.soton.ac.uk)
 *  @created 14 May 2013
 *	@version $Author$, $Revision$, $Date$
 */
public class AudioClassifierTest
{
	/**
	 * 	A provider for feature vectors for the sample buffers.
	 *
	 *	@author David Dupplaw (dpd@ecs.soton.ac.uk)
	 *  @created 8 May 2013
	 */
	public static class SamplesFeatureProvider implements FeatureExtractor<DoubleFV,SampleBuffer>
	{
		/** The MFCC processor */
		private final MFCCJAudio mfcc = new MFCCJAudio();

		@Override
		public DoubleFV extractFeature( final SampleBuffer buffer )
		{
			// Calculate the MFCCs
			final double[][] mfccs = this.mfcc.calculateMFCC( buffer );

			// The output vector
			final double[] values = new double[mfccs[0].length];

			if( mfccs.length > 1 )
			{
				// Average across the channels
				for( int i = 0; i < mfccs[0].length; i++ )
				{
					double acc = 0;
					for( int j = 0; j < mfccs.length; j++ )
						acc += mfccs[j][i];
					acc /= mfccs.length;
					values[i] = acc;
				}
			}
			else
				// Copy the mfccs
				System.arraycopy( mfccs[0], 0, values, 0, values.length );

			// Return the new DoubleFV
			return new DoubleFV( values );
		}
	}

	/**
	 * 	Use the OpenIMAJ experiment platform to cross-validate the dataset using the SVM annotator.
	 *	@param data The dataset
	 *	@throws IOException
	 */
	public static void crossValidate( final GroupedDataset<String,
			? extends ListDataset<List<SampleBuffer>>, List<SampleBuffer>> data ) throws IOException
	{
		// Flatten the dataset, and create a random group split operation we can use
		// to get the validation/training data.
		final StratifiedGroupedKFold<String, SampleBuffer> splits =
				new StratifiedGroupedKFold<String, SampleBuffer>( 5 );
//		final GroupedRandomSplits<String, SampleBuffer> splits =
//				new GroupedRandomSplits<String,SampleBuffer>(
//						DatasetAdaptors.flattenListGroupedDataset( data ),
//						data.numInstances()/2, data.numInstances()/2 );

		final CMAggregator<String> cma = new CMAggregator<String>();

		// Loop over the validation data.
		for( final ValidationData<GroupedDataset<String, ListDataset<SampleBuffer>, SampleBuffer>> vd :
				splits.createIterable( DatasetAdaptors.flattenListGroupedDataset( data ) ) )
		{
			// For this validation, create the annotator with the feature extractor and train it.
			final SVMAnnotator<SampleBuffer,String> ann = new SVMAnnotator<SampleBuffer,String>(
					new SamplesFeatureProvider() );

			ann.train( AnnotatedObject.createList( vd.getTrainingDataset() ) );

			// Create a classification evaluator that will do the validation.
			final ClassificationEvaluator<CMResult<String>, String, SampleBuffer> eval =
					new ClassificationEvaluator<CMResult<String>, String, SampleBuffer>(
						ann, vd.getValidationDataset(),
						new CMAnalyser<SampleBuffer, String>(CMAnalyser.Strategy.SINGLE) );

			final Map<SampleBuffer, ClassificationResult<String>> guesses = eval.evaluate();
			final CMResult<String> result = eval.analyse(guesses);
			cma.add( result );

			System.out.println( result.getDetailReport() );
		}

		System.out.println( cma.getAggregatedResult().getDetailReport() );
	}

	/**
	 *
	 *	@param args
	 * @throws IOException
	 */
	public static void main( final String[] args ) throws IOException
	{
		// Virtual file system for music speech corpus
		final GroupedDataset<String, ? extends ListDataset<List<SampleBuffer>>, List<SampleBuffer>>
			musicSpeechCorpus = new	VFSGroupDataset<List<SampleBuffer>>(
						"/data/music-speech-corpus/music-speech/wavfile/train",
						new OneSecondClipReader() );

		System.out.println( "Corpus size: "+musicSpeechCorpus.numInstances() );

		// Cross-validate the audio classifier trained on speech & music.
		final HashMap<String,String[]> regroup = new HashMap<String, String[]>();
		regroup.put( "speech", new String[]{ "speech" } );
		regroup.put( "non-speech", new String[]{ "music", "m+s", "other" } );
		AudioClassifierTest.crossValidate( DatasetAdaptors.getRegroupedDataset(
				musicSpeechCorpus, regroup ) );

//		// Create a new feature extractor for the sample buffer
//		final SamplesFeatureProvider extractor = new SamplesFeatureProvider();
//
//		// Create an SVM annotator
//		final SVMAnnotator<SampleBuffer,String> svm = new SVMAnnotator<SampleBuffer,String>( extractor );
//
//		AudioClassifier<String> ac = new AudioClassifier<String>( svm );
//
//		// Create the training data
//		final List<IndependentPair<AudioStream,String>> trainingData = new ArrayList<IndependentPair<AudioStream,String>>();
//		trainingData.add( new IndependentPair<AudioStream,String>( AudioDatasetHelper.getAudioStream(
//				musicSpeechCorpus.getInstances( "music" ) ), "non-speech" ) );
//		trainingData.add( new IndependentPair<AudioStream,String>( AudioDatasetHelper.getAudioStream(
//				musicSpeechCorpus.getInstances( "speech" ) ), "speech" ) );
//
//		// Train the classifier
//		ac.train( trainingData );
	}
}