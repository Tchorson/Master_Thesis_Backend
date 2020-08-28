package com.tchorek.routes_collector.database.controller;

import com.tchorek.routes_collector.database.model.DailyRecord;
import com.tchorek.routes_collector.database.model.HistoryTracks;
import com.tchorek.routes_collector.encryption.Encryptor;
import com.tchorek.routes_collector.encryption.EncryptorProperties;
import com.tchorek.routes_collector.message.json.BluetoothData;
import com.tchorek.routes_collector.database.json.ServerData;
import com.tchorek.routes_collector.database.service.DatabaseService;
import com.tchorek.routes_collector.monitoring.service.MonitoringService;
import com.tchorek.routes_collector.utils.Mapper;
import com.tchorek.routes_collector.utils.Timer;
import com.tchorek.routes_collector.utils.Validator;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Log4j2
@Controller
public class UserDataController {

    @Autowired
    DatabaseService databaseService;

    @Autowired
    MonitoringService monitoringService;

    @Autowired
    Validator validator;

    @Autowired
    EncryptorProperties encryptorProperties;

    @PostMapping(path = "/point", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity saveUserLocation(@RequestBody BluetoothData data){
        data.setUser(decryptUser(data.getUser()));
        return analyzeData(data);
    }

    @GetMapping(path = "/daily-data")
    public ResponseEntity getDailyData(){
        Iterable<DailyRecord> dailyData = databaseService.getDailyData();
        dailyData.forEach(data -> data.setPhoneNumber(encryptUser(data.getPhoneNumber())));
        return ResponseEntity.ok().body(dailyData);
    }

    @GetMapping(path = "/find-users", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getUsersWhoMetUserRecently(@RequestBody ServerData phoneWithTime){
        phoneWithTime.setUserData(decryptUser(phoneWithTime.getUserData()));
        Set<String> users = databaseService.getUsersWhoMetUser(phoneWithTime);
        users.forEach(this::encryptUser);
        return ResponseEntity.ok().body(users);
    }

    @GetMapping(path = "/user-daily-route", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getUserRoute(@RequestBody String phoneNumber){
        Iterable<DailyRecord> records = databaseService.getUserDailyRoute(decryptUser(phoneNumber));
        records.forEach(record -> record.setPhoneNumber(encryptUser(record.getPhoneNumber())));
        return ResponseEntity.ok().body(records);
    }

    @GetMapping(path = "/user-history", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getUserHistory(@RequestBody String phoneNumber){
        List<HistoryTracks> userPathsHistory = databaseService.getUserHistory(decryptUser(phoneNumber));
        userPathsHistory.forEach(user -> user.setPhoneNumber(encryptUser(user.getPhoneNumber())));
        return ResponseEntity.ok().body(userPathsHistory);
    }

    @GetMapping(path = "/users-data", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity getAllUsersFromParticularPlaceAndTime(@RequestBody ServerData locationAndTime){
        Set<String> users = databaseService.getAllUsersFromParticularPlaceAndTime(decryptUser(locationAndTime.getUserData()), locationAndTime.getStartDate(), locationAndTime.getStopDate());
        users.forEach(this::encryptUser);
        return ResponseEntity.ok().body(users);
    }

    private ResponseEntity analyzeData(BluetoothData data){
        String deviceName = data.getDeviceName();
        String user = data.getUser();
        if(validator.isDeviceValid(deviceName) && monitoringService.checkIfUserIsRegistered(user)){
            if(monitoringService.isUserBeforeTime(user)){
                log.warn("USER {} ARRIVED BEFORE SCHEDULED TIME: {}", user, Timer.getCurrentTimeInSeconds());
            }

            databaseService.saveTrackOfUser(Mapper.mapJsonToObject(data));
            monitoringService.saveUserActivity(Mapper.mapJsonToObject(data));
            log.debug("USER {} REACHED {} DEVICE", user, Timer.getCurrentTimeInSeconds());
            return ResponseEntity.ok(HttpStatus.OK);
        }
        if(validator.isDeviceValid(deviceName)){
            log.warn("UNAUTHORIZED USER IN THE AREA: {} at time {}", user, Timer.getFullDate(Timer.getCurrentTimeInSeconds()));
        }
        else {
            log.warn("UNKNOWN DEVICE ATTEMPT  {} at time {}", deviceName, Timer.getFullDate(Timer.getCurrentTimeInSeconds())) ;
        }
        return ResponseEntity.ok(HttpStatus.UNAUTHORIZED);
    }

    private String encryptUser(String data){
        return Encryptor.encrypt(data, encryptorProperties.getKey());
    }

    private String decryptUser(String data){
        return Encryptor.decrypt(data, encryptorProperties.getKey());
    }
}
