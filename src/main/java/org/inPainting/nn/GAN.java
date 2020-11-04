package org.inPainting.nn;

import javafx.scene.image.WritableImage;
import lombok.Getter;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.*;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.BaseTrainingListener;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.*;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.inPainting.nn.res.NetResult;
import org.inPainting.utils.ImageLoader;

import java.util.List;
import java.util.function.Supplier;

import static org.inPainting.utils.LayerUtils.*;

public class GAN {
    private static final IUpdater UPDATER_ZERO = Sgd.builder().learningRate(0.00).build();

    public static final double LEARNING_RATE = 2E-4;
    public static final double LEARNING_BETA1 = 5E-1;
    public static final double LEARNING_LAMBDA = 10E+1;
    public static final int[] _MergedNetInputShape = {1,4,256,256};

    public interface DiscriminatorProvider {
        ComputationGraph provide(IUpdater updater);
    }

    protected Supplier<ComputationGraph> generatorSupplier;
    protected DiscriminatorProvider discriminatorSupplier;

    @Getter
    protected ComputationGraph discriminator;
    @Getter
    protected ComputationGraph network;

    protected IUpdater updater = new Adam(LEARNING_RATE);
    protected IUpdater biasUpdater;
    protected OptimizationAlgorithm optimizer;
    protected GradientNormalization gradientNormalizer;
    protected double gradientNormalizationThreshold;
    protected WorkspaceMode trainingWorkSpaceMode;
    protected WorkspaceMode inferenceWorkspaceMode;
    protected CacheMode cacheMode;
    protected long seed;
    protected ImageLoader imageLoader = new ImageLoader(GAN._MergedNetInputShape);


    public GAN(Builder builder) {
        this.generatorSupplier = builder.generator;
        this.discriminatorSupplier = builder.discriminator;
        this.updater = builder.iUpdater;
        this.biasUpdater = builder.biasUpdater;
        this.optimizer = builder.optimizationAlgo;
        this.gradientNormalizer = builder.gradientNormalization;
        this.gradientNormalizationThreshold = builder.gradientNormalizationThreshold;
        this.trainingWorkSpaceMode = builder.trainingWorkspaceMode;
        this.inferenceWorkspaceMode = builder.inferenceWorkspaceMode;
        this.cacheMode = builder.cacheMode;
        this.seed = builder.seed;

        this.defineGan();
    }

    public GAN(ComputationGraph discriminator, ComputationGraph gan) {
        this.network = gan;
        this.discriminator = discriminator;
    }

    public WritableImage drawOutput(INDArray Picture, INDArray Mask, int width, int height) {
        return imageLoader.drawImage(network.output(Picture, Mask)[1], width, height);
    }

    public NetResult getOutput(INDArray Picture, INDArray Mask) {
        return new NetResult(network.output(Picture,Mask));
    }

    public Evaluation evaluateGan(DataSetIterator data) {
        return network.evaluate(data);
    }

    public Evaluation evaluateGan(DataSetIterator data, List<String> labelsList) {
        return network.evaluate(data, labelsList);
    }

    public void setDiscriminatorListeners(BaseTrainingListener[] listeners) {
        discriminator.setListeners(listeners);
    }

    public void setGanListeners(BaseTrainingListener[] listeners) {
        network.setListeners(listeners);
    }

    public void fit(MultiDataSetIterator realData, int numEpochs) {
        for (int i = 0; i < numEpochs; i++) {
            while (realData.hasNext()) {
                MultiDataSet next = (MultiDataSet) realData.next();
                fit(next,true);
            }
            realData.reset();
        }
    }

    public void fit(MultiDataSet next, boolean trainDiscriminator) {
        //INDArray realImage = next.getLabels()[0];
        /*for (int i = 0; i < discriminator.getLayers().length; i++) {
            if (discriminatorLearningRates[i] != null) {
                discriminator.setLearningRate(i, discriminatorLearningRates[i]);
            }
        }*/
        if (trainDiscriminator) {
            INDArray[] ganOutput = network.output(next.getFeatures());

            // Real images are marked as "0;1", fake images at "1;0".
            //DataSet realSet = new DataSet(next.getLabels()[0], Outputs.REAL());
            //DataSet fakeSet = new DataSet(ganOutput[1], Outputs.FAKE());

            MultiDataSet realSet = new MultiDataSet(
                    new INDArray[]{
                            next.getLabels()[0], //expected output
                            next.getFeatures()[1] //mask
                    },new INDArray[]{
                    Outputs.REAL()
            });

            MultiDataSet fakeSet = new MultiDataSet(
                    new INDArray[]{
                            ganOutput[1], //gan output
                            next.getFeatures()[1] //mask
                    },new INDArray[]{
                    Outputs.FAKE()
            });

            for (int i = 0; i < 3; i++)
                discriminator.fit(fakeSet);
            for (int i = 0; i < 2; i++)
                discriminator.fit(realSet);



            realSet.detach();
            fakeSet.detach();
        }

        // Generate a new set of adversarial examples and try to mislead the discriminator.
        // by labeling the fake images as real images we reward the generator when it's output
        // tricks the discriminator.
        //INDArray adversarialExamples = Nd4j.rand(new int[]{batchSize, latentDim});
        //INDArray misleadingLabels = Nd4j.zeros(batchSize, 1);

        //DataSet adversarialSet = new DataSet(next.getFeatures(), realone);
        // Set learning rate of discriminator part of gan to zero.
        /*for (int i = generator.getLayers().length; i < gan.getLayers().length; i++) {
            gan.setLearningRate(i, 0.0);
        }*/

        // Fit the Pix2PixGAN on the adversarial set, trying to fool the discriminator by generating
        // better fake images.
        network.fit(new MultiDataSet(
                new INDArray[]{
                        next.getFeatures()[0],
                        next.getFeatures()[1]
                },
                new INDArray[]{
                        Outputs.REAL(),
                        next.getLabels()[0]
                })
        );

        // Update the discriminator in the Pix2PixGAN network
        updateGanWithDiscriminator();
    }

    private void defineGan() {
        discriminator = discriminatorSupplier.provide(updater);
        discriminator.init();

        ComputationGraph ganDiscriminator = discriminatorSupplier.provide(UPDATER_ZERO);
        ganDiscriminator.init();

        network = GAN.NET(updater);
        network.init();

        // Update the discriminator in the Pix2PixGAN network
        updateGanWithDiscriminator();
    }

    public static ComputationGraph NET(IUpdater updater) {

        double nonZeroBias = 1;
        int inputChannels = 4;
        int outputChannels = 3;
        int maskChannels = 1;
        int[] doubleKernel = {2,2};
        int[] doubleStride = {2,2};
        int[] noStride = {1,1};

        return new ComputationGraph(new NeuralNetConfiguration.Builder()
                .updater(updater)
                .l2(5*1E-4)
                .graphBuilder()
                .allowDisconnected(true)
                .addInputs("Input", "Mask")
                //rgb 256x256x3x1 + m 256x256x1x1
                .setInputTypes(InputType.convolutional(
                        _MergedNetInputShape[2],
                        _MergedNetInputShape[3],
                        _MergedNetInputShape[1] - maskChannels
                ),InputType.convolutional(
                        _MergedNetInputShape[2],
                        _MergedNetInputShape[3],
                        _MergedNetInputShape[1] - outputChannels
                ))

                //Generator
                //Encoder 256x256x4 -> 128x128x16
                //Merging Input with mask
                .addVertex("InputGENmerge0",
                        new MergeVertex(),
                        "Input","Mask")

                .addLayer("GENCNN1",
                        convInitSame(
                                ((inputChannels)),
                                ((inputChannels*4)),
                                doubleStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "InputGENmerge0")
                //Encoder 128x128x16 -> 64x64x64
                .addLayer("GENCNN2",
                        convInitSame(
                                (inputChannels*4),
                                (inputChannels*16),
                                doubleStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "GENCNN1")
                //Encoder 64x64x64 -> 32x32x256
                .addLayer("GENCNN3",
                        convInitSame(
                                (inputChannels*16),
                                (inputChannels*64),
                                doubleStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "GENCNN2")
                //Encoder 32x32x256 -> 16x16x1024
                .addLayer("GENCNN4",
                        convInitSame(
                                (inputChannels*64),
                                (inputChannels*256),
                                doubleStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "GENCNN3")
                //Encoder 16x16x1024 -> 8x8x4096
                .addLayer("GENCNN5",
                        convInitSame(
                                (inputChannels*256),
                                (inputChannels*1024),
                                doubleStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "GENCNN4")
                //Decoder Vertex 8x8x4096 -> 16x16x1024
                .addVertex("GENRV1",
                        reshape(_MergedNetInputShape[0], _MergedNetInputShape[1]*256, _MergedNetInputShape[2]/16, _MergedNetInputShape[3]/16),
                        "GENCNN5")
                //Merging Decoder with GENCNN1
                .addVertex("GENmerge1",
                        new MergeVertex(),
                        "GENCNN4","GENRV1")
                //Decoder 16x16x1024
                .addLayer("GENCNN6",
                        convInitSame(
                                (inputChannels*256*2),
                                (inputChannels*256),
                                noStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "GENmerge1")
                //Decoder Vertex 16x16x1024 -> 32x32x256x1
                .addVertex("GENRV2",
                        reshape(_MergedNetInputShape[0], _MergedNetInputShape[1]*64, _MergedNetInputShape[2]/8, _MergedNetInputShape[3]/8),
                        "GENCNN6")
                //Merging Decoder with Input
                .addVertex("GENmerge2",
                        new MergeVertex(),
                        "GENCNN3","GENRV2")
                //Decoder 32x32x256
                .addLayer("GENCNN7",
                        convInitSame(
                                (inputChannels*64*2),
                                (inputChannels*64),
                                noStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "GENmerge2")
                //Decoder Vertex 32x32x256 -> 64x64x64
                .addVertex("GENRV3",
                        reshape(_MergedNetInputShape[0], _MergedNetInputShape[1]*16, _MergedNetInputShape[2]/4, _MergedNetInputShape[3]/4),
                        "GENCNN7")
                //Merging Decoder with Input
                .addVertex("GENmerge3",
                        new MergeVertex(),
                        "GENCNN2","GENRV3")
                //Decoder 64x64x64
                .addLayer("GENCNN8",
                        convInitSame(
                                (inputChannels*16*2),
                                (inputChannels*16),
                                noStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "GENmerge3")
                //Decoder Vertex 64x64x64x1 -> 128x128x*16
                .addVertex("GENRV4",
                        reshape(_MergedNetInputShape[0], _MergedNetInputShape[1]*4, _MergedNetInputShape[2]/2, _MergedNetInputShape[3]/2),
                        "GENCNN8")


                //Merging Decoder with Input
                .addVertex("GENmerge4",
                        new MergeVertex(),
                        "GENCNN1","GENRV4")
                //Decoder 128x128x16x1
                .addLayer("GENCNN9",
                        convInitSame(
                                (inputChannels*4*2),
                                (inputChannels*4),
                                Activation.LEAKYRELU),
                        "GENmerge4")
                //Decoder Vertex 128x128x*16 -> 256x256x4
                .addVertex("GENRV5",
                        reshape(_MergedNetInputShape[0], _MergedNetInputShape[1], _MergedNetInputShape[2], _MergedNetInputShape[3]),
                        "GENCNN9")

                //Merging Decoder with Input
                .addVertex("GENmerge5",
                        new MergeVertex(),
                        "InputGENmerge0","GENRV5")
                //Decoder 256x256x4
                .addLayer("GENCNN10",
                        convInitSame(
                                (inputChannels*2),
                                (outputChannels),
                                Activation.LEAKYRELU),
                        "GENmerge5")

                //Decoder Loss
                .addLayer("GENCNNLoss", new CnnLossLayer.Builder(LossFunctions.LossFunction.XENT)
                        .activation(Activation.SIGMOID)
                        .build(),"GENCNN10")


                //Discriminator
                .addLayer("DISCNN1", new ConvolutionLayer.Builder(new int[]{11,11}, new int[]{4, 4})
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                        .convolutionMode(ConvolutionMode.Truncate)
                        .activation(Activation.LEAKYRELU)
                        .nIn(outputChannels + maskChannels)
                        .nOut(96)
                        .build(),"GENCNN10", "Mask")
                .addLayer("DISLRN1", new LocalResponseNormalization.Builder().build(),"DISCNN1")
                .addLayer("DISSL1", new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                        .kernelSize(3,3)
                        .stride(2,2)
                        .padding(1,1)
                        .build(),"DISLRN1")
                .addLayer("DISCNN2", new ConvolutionLayer.Builder(new int[]{5,5}, new int[]{1,1}, new int[]{2,2})
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                        .activation(Activation.LEAKYRELU)
                        .convolutionMode(ConvolutionMode.Truncate)
                        .nOut(256)
                        .biasInit(nonZeroBias)
                        .build(),"DISSL1")
                .addLayer("DISSL2", new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX, new int[]{3, 3}, new int[]{2, 2})
                        .convolutionMode(ConvolutionMode.Truncate)
                        .build(),"DISCNN2")
                .addLayer("DISLRN2", new LocalResponseNormalization.Builder().build(),"DISSL2")
                .addLayer("DISCNN3", new ConvolutionLayer.Builder()
                        .activation(Activation.LEAKYRELU)
                        .kernelSize(3,3)
                        .stride(1,1)
                        .convolutionMode(ConvolutionMode.Same)
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                        .nOut(384)
                        .build(),"DISLRN2")
                .addLayer("DISCNN4", new ConvolutionLayer.Builder(new int[]{3,3}, new int[]{1,1})
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                        .activation(Activation.LEAKYRELU)
                        .nOut(384)
                        .biasInit(nonZeroBias)
                        .build(),"DISCNN3")
                .addLayer("DISCNN5", new ConvolutionLayer.Builder(new int[]{3,3}, new int[]{1,1})
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                        .activation(Activation.LEAKYRELU)
                        .nOut(256)
                        .biasInit(nonZeroBias)
                        .build(),"DISCNN4")
                .addLayer("DISSL3", new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX, new int[]{3,3}, new int[]{2,2})
                        .convolutionMode(ConvolutionMode.Truncate)
                        .build(),"DISCNN5")
                .addLayer("DISFFN1", new DenseLayer.Builder()
                        .weightInit(new NormalDistribution(0, 5E-3))
                        .activation(Activation.LEAKYRELU)
                        .nOut(4096)
                        .biasInit(nonZeroBias)
                        .build(),"DISSL3")
                .addLayer("DISFFN2", new DenseLayer.Builder()
                        .weightInit(new NormalDistribution(0, 5E-3))
                        .activation(Activation.LEAKYRELU)
                        .nOut(2047)
                        .biasInit(nonZeroBias)
                        .dropOut(0.5)
                        .build(),"DISFFN1")
                .addLayer("DISLoss", new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
                        .nOut(2)
                        .activation(Activation.SOFTMAX)
                        .weightInit(new NormalDistribution(0, 5E-3))
                        .biasInit(0.1)
                        .build(),"DISFFN2")

                .setOutputs("DISLoss","GENCNNLoss") //Discriminator output, Generator loss
                .build());
    }

    /**
     * After the discriminator has been trained, we update the respective parts of the GAN network
     * as well.
     */
    private void updateGanWithDiscriminator() {
        int genLayerCount = network.getLayers().length - discriminator.getLayers().length; //Position of first Discriminator Layer
        for (int i = genLayerCount; i < network.getLayers().length; i++) {
            network.getLayer(i).setParams(discriminator.getLayer(i - genLayerCount).params());
            network.getLayer(i).setMaskArray(discriminator.getLayer(i-genLayerCount).getMaskArray());
        }
    }


    /**
     * GAN builder, used as a starting point for creating a MultiLayerConfiguration or
     * ComputationGraphConfiguration.<br>
     */
    public static class Builder implements Cloneable {
        protected Supplier<ComputationGraph> generator;
        protected DiscriminatorProvider discriminator;

        protected IUpdater iUpdater = new Sgd();
        protected IUpdater biasUpdater = null;
        protected long seed = System.currentTimeMillis();
        protected OptimizationAlgorithm optimizationAlgo = OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT;
        protected GradientNormalization gradientNormalization = GradientNormalization.None;
        protected double gradientNormalizationThreshold = 1.0;

        protected WorkspaceMode trainingWorkspaceMode = WorkspaceMode.ENABLED;
        protected WorkspaceMode inferenceWorkspaceMode = WorkspaceMode.ENABLED;
        protected CacheMode cacheMode = CacheMode.NONE;

        public Builder() {
        }

        /**
         * Set the image discriminator of the GAN.
         *
         * @param discriminator MultilayerNetwork
         * @return Builder
         */
        public GAN.Builder discriminator(DiscriminatorProvider discriminator) {
            this.discriminator = discriminator;
            return this;
        }

        /**
         * Random number generator seed. Used for reproducibility between runs
         */
        public GAN.Builder seed(long seed) {
            this.seed = seed;
            Nd4j.getRandom().setSeed(seed);
            return this;
        }

        /**
         * Optimization algorithm to use. Most common: OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT
         *
         * @param optimizationAlgo Optimization algorithm to use when training
         */
        public GAN.Builder optimizationAlgo(OptimizationAlgorithm optimizationAlgo) {
            this.optimizationAlgo = optimizationAlgo;
            return this;
        }


        /**
         * Gradient updater configuration. For example, {@link org.nd4j.linalg.learning.config.Adam}
         * or {@link org.nd4j.linalg.learning.config.Nesterovs}<br>
         * Note: values set by this method will be applied to all applicable layers in the network, unless a different
         * value is explicitly set on a given layer. In other words: values set via this method are used as the default
         * value, and can be overridden on a per-layer basis.
         *
         * @param updater Updater to use
         */
        public GAN.Builder updater(IUpdater updater) {
            this.iUpdater = updater;
            return this;
        }

        /**
         * Gradient updater configuration, for the biases only. If not set, biases will use the updater as
         * set by {@link #updater(IUpdater)}<br>
         * Note: values set by this method will be applied to all applicable layers in the network, unless a different
         * value is explicitly set on a given layer. In other words: values set via this method are used as the default
         * value, and can be overridden on a per-layer basis.
         *
         * @param updater Updater to use for bias parameters
         */
        public GAN.Builder biasUpdater(IUpdater updater) {
            this.biasUpdater = updater;
            return this;
        }

        /**
         * Gradient normalization strategy. Used to specify gradient renormalization, gradient clipping etc.
         * See {@link GradientNormalization} for details<br>
         * Note: values set by this method will be applied to all applicable layers in the network, unless a different
         * value is explicitly set on a given layer. In other words: values set via this method are used as the default
         * value, and can be overridden on a per-layer basis.
         *
         * @param gradientNormalization Type of normalization to use. Defaults to None.
         * @see GradientNormalization
         */
        public GAN.Builder gradientNormalization(GradientNormalization gradientNormalization) {
            this.gradientNormalization = gradientNormalization;
            return this;
        }

        /**
         * Threshold for gradient normalization, only used for GradientNormalization.ClipL2PerLayer,
         * GradientNormalization.ClipL2PerParamType, and GradientNormalization.ClipElementWiseAbsoluteValue<br>
         * Not used otherwise.<br>
         * L2 threshold for first two types of clipping, or absolute value threshold for last type of clipping.<br>
         * Note: values set by this method will be applied to all applicable layers in the network, unless a different
         * value is explicitly set on a given layer. In other words: values set via this method are used as the default
         * value, and can be overridden on a per-layer basis.
         */
        public GAN.Builder gradientNormalizationThreshold(double threshold) {
            this.gradientNormalizationThreshold = threshold;
            return this;
        }

        public GAN build() {
            return new GAN(this);
        }
    }

    public static class Outputs {
        public static INDArray REAL(){
            INDArray real_one = Nd4j.zeros(1,2);
            for(int i = 0; i<2; i++)
                real_one.putScalar(i,i);

            return real_one;
        }

        public static INDArray FAKE(){
            INDArray fake_one = Nd4j.zeros(1,2);
            fake_one.putScalar(0, 1);
            fake_one.putScalar(1, 0);

            return fake_one;
        }
    }
}