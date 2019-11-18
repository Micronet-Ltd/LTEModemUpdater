package com.micronet.a317modemupdater.interfaces;

public interface UpdateState {

    void couldNotUploadPrecheck();
    void couldNotConfigureRild();
    void couldNotSetupPort();
    void couldNotCommunicateWithModem();

    void initialModemTypeAndVersion(String modemType, String modemVersion);
    void noUpdateFileForModem();
    void alreadyUpdated(String modemVersion);
    void attemptingToUpdate(String modemVersion);

    void sendingUpdateFileToModem();
    void loadedUpdateFile(int max);
    void errorLoadingUpdateFile();
    void errorConnectingToModemToSendUpdateFile();
    void updateSendProgress(int packetsSent);
    void errorFileNotSentSuccessfully();
    void fileSentSuccessfully();
    void errorFileNotValidated();
    void fileValidatedSuccessfully();
    void errorFileNotValidatedAndUpdateProcessNotStarting();
    void updateProcessStarting();

    void updatedModemFirmwareVersion(String modemVersion);
    void errorRestartModem();

    void successfullyUpdatedUploadingLogs();
    void failureUpdatingUploadingLogs();

    void delayedShutdown(final int delaySeconds);
    void cancelShutdown();
}
