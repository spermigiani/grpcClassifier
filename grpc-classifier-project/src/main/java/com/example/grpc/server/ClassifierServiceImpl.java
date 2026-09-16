package com.example.grpc.server;

import com.example.grpc.classifier.ClassifierServiceGrpc;
import com.example.grpc.classifier.ClassifyRequest;
import com.example.grpc.classifier.ClassifyResponse;
import edu.mit.csail.sdg.dynextension.Classifier;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
/**
 * Implementation of ClassifierService.
 * On construction it builds 2 lists, {@code posInstanceToClassify} and {@code negInstanceToClassify}, of
 *  objects from the external classifier.jar library.
 * For every incoming request it calls {@code classifyAlloyInstance(String)} on each
 * instance and counts the number of positive and negative results, returning a summary in
 * a single result string returned to the client.
 */
public class ClassifierServiceImpl extends ClassifierServiceGrpc.ClassifierServiceImplBase {

    private static final Logger logger = Logger.getLogger(ClassifierServiceImpl.class.getName());

    /** Directory of Classifier instances to instantiate at startup. */
    private static final String INSTANCE_DIRECTORY = "xml_instances/";

    /** The pool of classifier instances used to evaluate every request. */
    private final List<Classifier> posInstanceToClassify = new ArrayList<>();
    private final List<Classifier> negInstanceToClassify = new ArrayList<>();


    public ClassifierServiceImpl() {
        try {
            fillPosNegInstances();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        logger.info(() -> "Initialized " + posInstanceToClassify.size() + " Classifier positives instance(s) and " + negInstanceToClassify.size() + " Classifier positives instance(s). ");
    }

    /**
     * Build Classifiers list with positives and negatives instances.
     */
    private void fillPosNegInstances( ) throws IOException {
        File folder = new File(INSTANCE_DIRECTORY);
        if (!folder.exists()) {
            throw new RuntimeException("missing instances directory: " + INSTANCE_DIRECTORY);
        }
        File[] fileList = folder.listFiles();
        for (int i = 0; i < fileList.length; i++) {
          if (fileList[i].isFile() ) {
              String fileName = fileList[i].getName();
              if( fileName.charAt(0) == '.') continue;
              if(fileName.endsWith(".xml")) {
                    if(fileName.contains("negative")) {
                        negInstanceToClassify.add(new Classifier(INSTANCE_DIRECTORY+fileList[i].getName()));

                    } else  {
                        posInstanceToClassify.add(new Classifier(INSTANCE_DIRECTORY+fileList[i].getName()));
                    }
              }

          }

        }  
    }

    /**
     * Evaluate a candidate post-condition (request) in all instances and generate a String response.
     */
    @Override
    public void classify(ClassifyRequest request, StreamObserver<ClassifyResponse> responseObserver) {
        int posFail = 0;
        int posPass = 0;
        int negFail = 0;
        int negReject = 0;
        int total;// = 0;
        try {

            String candidatePC = request.getText();
            //Analizar tipos de respuesta para cada Classifier:
            // String respuesta = pos[i].classifyAlloyInstance(text) //llamarlo candidatePC
            //respuesta == true, respuesta ==false o respuesta == empty

            for (Classifier classifier : posInstanceToClassify) {
                String pResult = classifier.classifyAlloyInstance(candidatePC).toString();
                if (pResult.equals("true")) {
                    posPass++;
                } else if (pResult.equals("false")) {
                    posFail++;
                }

            }

            for (Classifier classifier : negInstanceToClassify) {
                String nResult = classifier.classifyAlloyInstance(candidatePC).toString();
                if (nResult.equals("false")) {
                    negReject++;
                } else if (nResult.equals("true")) {
                    negFail++;
                }

            }
            total = posFail + posPass + negFail + negReject;
        } catch (IOException e) {
            logger.warning("Classifier threw an exception, treating as a negative vote: " + e.getMessage());
            responseObserver.onError(e);
            return;
        }
        //formato de String? 
        String result = "pos_pass= " + posPass +"\n neg_reject= " + negReject + "\n pos_fail= " + posFail +" \n neg_fail= " + negFail + " \n total=" + total;

        ClassifyResponse response = ClassifyResponse.newBuilder()
                .setResult(result)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }



    /*List<Boolean> votes = new ArrayList<>(instanceToClassify.size());
        int positiveVotes = 0;

        for (Classifier classifier : instanceToClassify) {
            boolean vote;
            String instanceResult;
            try {
                instanceResult = classifier.classifyAlloyInstance(text);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Classifier threw an exception, treating as a negative vote", e);
                vote = false;
            }
            votes.add(vote);
            if (vote) {
                positiveVotes++;
            }
        }

        boolean accepted = instanceToClassify.isEmpty()
                ? false
                : positiveVotes * 2 > instanceToClassify.size(); // strict majority

        String result = String.format(
                "%s (%d/%d classifiers agree)",
                accepted ? "VALID" : "INVALID",
                positiveVotes,
                instanceToClassify.size());

        ClassifyResponse response = ClassifyResponse.newBuilder()
                .setResult(result)
                .setAccepted(accepted)
                .addAllVotes(votes)
                .build();
        */

   
}
