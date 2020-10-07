package org.inPainting.nn;

import org.deeplearning4j.nn.conf.ConvolutionMode;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.distribution.NormalDistribution;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.Nesterovs;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.io.File;
import java.io.IOException;

import static org.inPainting.utils.LayerUtils.convInitSame;
import static org.inPainting.utils.LayerUtils.reshape;

public class NeuralNetwork {

    public static void saveNetworkGraph(ComputationGraph neuralNetwork, File file) {
        try {
            ModelSerializer.writeModel(neuralNetwork, file, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ComputationGraph loadNetworkGraph(File file) throws IOException {
        return ComputationGraph.load(file, true);
    }

    public static ComputationGraph getOldGenerator() {

        int[] _NetInputShape = {1,4,256,256};

        double nonZeroBias = 1;
        int inputChannels = 4;
        int outputChannels = 3;
        int[] doubleKernel = {2,2};
        int[] doubleStride = {2,2};
        int[] noStride = {1,1};

        return new ComputationGraph(new NeuralNetConfiguration.Builder()
                .updater(Adam.builder().learningRate(GAN.LEARNING_RATE).beta1(GAN.LEARNING_BETA1).build())
                .l2(5*1e-4)
                .graphBuilder()
                .allowDisconnected(true)
                .addInputs("Input","Mask")
                //m + rgb 256x256x4x1
                .setInputTypes(InputType.convolutional(256,256,3),InputType.convolutional(256,256,1))

                //Generator
                //Encoder 256x256x4 -> 128x128x16
                .addLayer("GENCNN1",
                        convInitSame(
                                ((inputChannels)),
                                ((inputChannels*4)),
                                doubleStride,
                                doubleKernel,
                                Activation.LEAKYRELU),
                        "Input","Mask")
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
                        reshape(_NetInputShape[0],_NetInputShape[1]*256,_NetInputShape[2]/16,_NetInputShape[3]/16),
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
                        reshape(_NetInputShape[0],_NetInputShape[1]*64,_NetInputShape[2]/8,_NetInputShape[3]/8),
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
                        reshape(_NetInputShape[0],_NetInputShape[1]*16,_NetInputShape[2]/4,_NetInputShape[3]/4),
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
                        reshape(_NetInputShape[0],_NetInputShape[1]*4,_NetInputShape[2]/2,_NetInputShape[3]/2),
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
                        reshape(_NetInputShape[0],_NetInputShape[1],_NetInputShape[2],_NetInputShape[3]),
                        "GENCNN9")
                //Merging Decoder with Input
                .addVertex("GENmerge5",
                        new MergeVertex(),
                        "Input","GENRV5")
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
                .setOutputs("GENCNNLoss")
                .build());

    }

    public static ComputationGraph getDiscriminator() {
        int seed = 123;
        int channels = 3;
        int mask = 1;
        double nonZeroBias = 1;

        return new ComputationGraph(new NeuralNetConfiguration.Builder()
                .weightInit(new NormalDistribution(0.0,1E-1))
                .updater(new Nesterovs(1e-2,9E-1))
                .biasUpdater(new Nesterovs(2e-2,9E-1))
                .gradientNormalization(GradientNormalization.RenormalizeL2PerLayer)
                .l2(5*1e-4)
                .miniBatch(false)
                .seed(seed)
                .graphBuilder()
                .addInputs("Input","Mask")
                //m + rgb 256x256x4x1
                .setInputTypes(InputType.convolutional(256,256,3),InputType.convolutional(256,256,1))

                //Discriminator
                .addLayer("DISCNN1", new ConvolutionLayer.Builder(new int[]{11,11}, new int[]{4, 4})
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                        .convolutionMode(ConvolutionMode.Truncate)
                        .activation(Activation.LEAKYRELU)
                        .nIn(channels + mask)
                        .nOut(96)
                        .build(),"Input","Mask")
                .addLayer("DISLRN1", new LocalResponseNormalization.Builder().build(),"DISCNN1")
                .addLayer("DISSL1", new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX)
                        .kernelSize(3,3)
                        .stride(2,2)
                        .padding(1,1)
                        .build(),"DISLRN1")
                .addLayer("DISCNN2", new ConvolutionLayer.Builder(new int[]{5,5}, new int[]{1,1}, new int[]{2,2})
                        .cudnnAlgoMode(ConvolutionLayer.AlgoMode.PREFER_FASTEST)
                        .convolutionMode(ConvolutionMode.Truncate)
                        .activation(Activation.LEAKYRELU)
                        .nOut(256)
                        .biasInit(nonZeroBias)
                        .build(),"DISSL1")
                .addLayer("DISSL2", new SubsamplingLayer.Builder(SubsamplingLayer.PoolingType.MAX, new int[]{3, 3}, new int[]{2, 2})
                        .convolutionMode(ConvolutionMode.Truncate)

                        .build(),"DISCNN2")
                .addLayer("DISLRN2", new LocalResponseNormalization.Builder().build(),"DISSL2")
                .addLayer("DISCNN3", new ConvolutionLayer.Builder()
                        .kernelSize(3,3)
                        .stride(1,1)
                        .convolutionMode(ConvolutionMode.Same)
                        .activation(Activation.LEAKYRELU)
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

                .setOutputs("DISLoss")
                .build()
        );
    }
}