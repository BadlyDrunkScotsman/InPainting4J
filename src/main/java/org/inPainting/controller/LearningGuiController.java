package org.inPainting.controller;

import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.optimize.api.BaseTrainingListener;
import org.deeplearning4j.optimize.listeners.PerformanceListener;
import org.nd4j.linalg.learning.config.Adam;
import org.inPainting.component.UIServerComponent;
import org.inPainting.nn.GAN;
import org.inPainting.nn.NeuralNetwork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
@Slf4j
public class LearningGuiController {

    private final IntegerProperty counterProperty = new SimpleIntegerProperty();

    @Autowired
    UIServerComponent uiServerComponent;

    @FXML
    private Button btnLoad;

    @FXML
    private Button btnSave;

    @FXML
    private Button btnTest;

    @FXML
    private ToggleButton btnTrain;

    @FXML
    private Label counterText;

    @FXML
    private Label counterEpoch;

    @FXML
    private CheckBox TrainD;

    @Autowired
    private CustomLearningGuiController customLearningGuiController;

    private GAN gan;

    @FXML
    private void initialize() {

        if (new File("gan.zip").exists() && new File("discriminator.zip").exists()){
            try {
                gan =  new GAN(NeuralNetwork.loadNetworkGraph(new File("discriminator.zip")), NeuralNetwork.loadNetworkGraph(new File("gan.zip")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else
            gan = new GAN.Builder().discriminator(updater -> {
                try {
                    log.info("Loading Discriminator");
                    return NeuralNetwork.loadNetworkGraph(new File("discriminator.zip"));
                } catch (IOException e) {
                    log.error("Error while loading discriminator network creating new one");
                    return NeuralNetwork.getDiscriminator();
                }
            }).updater(Adam.builder()
                    .learningRate(GAN.LEARNING_RATE)
                    .beta1(GAN.LEARNING_BETA1).build())
                    .build();

        log.info(gan.getNetwork().summary());

        customLearningGuiController.onSetNeuralNetwork(gan);
        customLearningGuiController.onInitialize();


        uiServerComponent.reinitialize(gan.getNetwork());
        gan.setDiscriminatorListeners(new BaseTrainingListener[]{new PerformanceListener(100, true)});
        //gan.setGanListeners(new BaseTrainingListener[]{new ScoreIterationListener(1000)});


        counterProperty.addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                counterText.setText("Iteration: " + newValue);
                counterEpoch.setText("Epoch: " + (long)((newValue.longValue()/(customLearningGuiController).getDataSize())+1));
            }
        });
    }

    public void loadAction(ActionEvent actionEvent) {
        if (new File("gan.zip").exists() && new File("discriminator.zip").exists()){
            try {
                gan =  new GAN(NeuralNetwork.loadNetworkGraph(new File("discriminator.zip")), NeuralNetwork.loadNetworkGraph(new File("gan.zip")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else
            gan = new GAN.Builder().discriminator(updater -> {
                try {
                    log.info("Loading Discriminator");
                    return NeuralNetwork.loadNetworkGraph(new File("discriminator.zip"));
                } catch (IOException e) {
                    log.error("Error while loading discriminator network creating new one");
                    return NeuralNetwork.getDiscriminator();
                }
            }).updater(Adam.builder()
                    .learningRate(GAN.LEARNING_RATE)
                    .beta1(GAN.LEARNING_BETA1)
                    .build()
            ).build();

        customLearningGuiController.onSetNeuralNetwork(gan);
        customLearningGuiController.onInitialize();

        uiServerComponent.reinitialize(gan.getNetwork());

        gan.setDiscriminatorListeners(new BaseTrainingListener[]{new PerformanceListener(100, true)});
        //gan.setGanListeners(new BaseTrainingListener[]{new PerformanceListener(100,true)});
        showAlert(Alert.AlertType.INFORMATION, "Success", "Neural network successfully loaded");
    }

    public void saveAction(ActionEvent actionEvent) {
        NeuralNetwork.saveNetworkGraph(gan.getNetwork(), new File("gan.zip"));
        NeuralNetwork.saveNetworkGraph(gan.getDiscriminator(), new File("discriminator.zip"));

        showAlert(Alert.AlertType.INFORMATION, "Success", "Neural network successfully saved");
    }

    public void trainAction(ActionEvent actionEvent) {
        boolean trainingMode = btnTrain.isSelected();
        btnLoad.setDisable(trainingMode);
        btnSave.setDisable(trainingMode);
        btnTest.setDisable(trainingMode);
        //btnTest.setDisable(trainingMode);
        if (btnTrain.isSelected())
            Platform.runLater(this::trainLoop);
    }

    public void testAction(ActionEvent actionEvent) {
        try {
            customLearningGuiController.onTestAction();
        } catch (RuntimeException e) {
            log.error("Test execution error", e);
            showAlert(Alert.AlertType.ERROR, "Test execution error", e.getMessage());
        }
    }

    @Synchronized
    private void trainLoop() {
        if (btnTrain.isSelected()) {
            counterProperty.setValue(counterProperty.get() + 1);
            customLearningGuiController.onTrainLoop(counterProperty.get(),TrainD.isSelected());

            Platform.runLater(this::trainLoop);
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

}