package com.douzone.server.service;

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.douzone.server.config.s3.AwsS3;
import com.douzone.server.config.security.handler.DecodeEncodeHandler;
import com.douzone.server.config.utils.ResponseDTO;
import com.douzone.server.dto.employee.SignupReqDTO;
import com.douzone.server.dto.vehicle.VehicleUpdateDTO;
import com.douzone.server.entity.Employee;
import com.douzone.server.entity.Vehicle;
import com.douzone.server.entity.VehicleImg;
import com.douzone.server.exception.EmpAlreadyExistException;
import com.douzone.server.exception.EmpNotFoundException;
import com.douzone.server.exception.ErrorCode;
import com.douzone.server.exception.ImgFileNotFoundException;
import com.douzone.server.repository.EmployeeRepository;
import com.douzone.server.repository.VehicleImgRepository;
import com.douzone.server.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.douzone.server.config.utils.Msg.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {
	private static final String METHOD_NAME = VehicleService.class.getName();
	private final EmployeeRepository employeeRepository;
	private final VehicleRepository vehicleRepository;
	private final VehicleImgRepository vehicleImgRepository;
	private final DecodeEncodeHandler decodeEncodeHandler;
	private final AwsS3 awsS3;

	@Value(value = "${year.current}")
	private String year;

	@Value(value = "${aws-client.path}")
	private String awsPath;

	@Transactional
	public Long register(SignupReqDTO signupReqDTO) {

		Employee employee = employeeRepository.findTop1ByOrderByIdDesc().orElseThrow(() -> new EmpNotFoundException(ErrorCode.EMP_NOT_FOUND));

		//년도 + 부서 + 사번
		StringBuilder sb = new StringBuilder();
		String deptId = String.format("%02d", signupReqDTO.getDeptId());
		String empId = String.format("%05d", employee.getId() + 1);
		sb.append(year).append(deptId).append(empId);

		String empNo = sb.toString();
		boolean exists = employeeRepository.existsByEmpNo(empNo);
		if (exists) {
			throw new EmpAlreadyExistException(ErrorCode.EMP_ALREADY_EXIST);
		}
		String password = decodeEncodeHandler.passwordEncode(signupReqDTO.getPassword());
		long id = employeeRepository.save(signupReqDTO.of(empNo, password)).getId();

		return id;
	}

	@Transactional
	public Long uploadProfileImg(List<MultipartFile> files, long id) {

		if (files.isEmpty() || files == null) {
			throw new ImgFileNotFoundException(ErrorCode.IMG_NOT_FOUND);
		}
		String basePath = "profile/";

		ArrayList<String> fileName = new ArrayList<String>();
		ArrayList<String> fileType = new ArrayList<String>();
		ArrayList<Long> fileLength = new ArrayList<Long>();
		try {
			for (int i = 0; i < files.size(); i++) {
				fileName.add(LocalDateTime.now().toString() + "_" + files.get(i).getOriginalFilename());
				fileType.add(files.get(i).getContentType());
				fileLength.add(files.get(i).getSize());
			}
			// 업로드 될 버킷 객체 url
			String[] uploadUrl = new String[files.size()];

			for (int i = 0; i < files.size(); i++) {
				try {
					uploadUrl[i] = awsS3.upload(files.get(i), basePath + fileName.get(i), fileType.get(i), fileLength.get(i));
				} catch (AmazonS3Exception e) {
					log.error("AmazonS3Exception : AdminService - uploadProfileImg " + e.getMessage());
					e.printStackTrace();
				} catch (IOException e) {
					log.error("IOException : AdminService - uploadProfileImg " + e.getMessage());
					e.printStackTrace();
				}
			}

			//데이터 베이스에 들어갈 url
			String profileImg = awsPath + uploadUrl[0];
			Employee employee = employeeRepository.findById(id).orElseThrow(() -> new EmpNotFoundException(ErrorCode.EMP_NOT_FOUND));

			employee.updateProfileImg(profileImg);
			return employee.getId();

		} catch (IllegalStateException e) {
			log.error("IllegalStateException : AdminService - uploadProfileImg " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			log.error("Exception : AdminService - uploadProfileImg " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	@Transactional
	public ResponseDTO createVehicle(VehicleUpdateDTO vehicleUpdateDTO, List<MultipartFile> files) {
		log.info(METHOD_NAME + "- createVehicle");
		Optional.ofNullable(files).filter(v -> !v.isEmpty())
				.orElseThrow(() -> new ImgFileNotFoundException(ErrorCode.IMG_NOT_FOUND));

		ArrayList<String> fileName = new ArrayList<>(), fileType = new ArrayList<>();
		ArrayList<Long> fileLength = new ArrayList<>();

		for (MultipartFile file : files) {
			fileName.add(LocalDateTime.now() + "_" + file.getOriginalFilename());
			fileType.add(file.getContentType());
			fileLength.add(file.getSize());
		}

		String[] uploadUrl = new String[files.size()];

		for (int i = 0; i < files.size(); i++) {
			try {
				uploadUrl[i] = awsS3.upload(files.get(i), "vehicle/" + fileName.get(i), fileType.get(i), fileLength.get(i));
			} catch (AmazonS3Exception | IOException ae) {
				log.error("차량 이미지 URL 업로드 에러" + METHOD_NAME, ae);
			}
		}
		StringBuilder filePath = new StringBuilder();
		for (String s : uploadUrl) filePath.append(s).append(" ");
		StringBuilder fType = new StringBuilder();
		for (String s : fileType) fType.append(s).append(" ");
		StringBuilder fSize = new StringBuilder();
		for (Long l : fileLength) fSize.append(l).append(" ");

		return Optional.of(new ResponseDTO())
				.map(res -> {
					Long vId = vehicleRepository.save(Vehicle.builder()
							.name(vehicleUpdateDTO.getName())
							.number(vehicleUpdateDTO.getNumber())
							.model(vehicleUpdateDTO.getModel())
							.color(vehicleUpdateDTO.getColor())
							.capacity(vehicleUpdateDTO.getCapacity())
							.build()).getId();
					vehicleImgRepository.save(VehicleImg.builder()
							.vehicle(Vehicle.builder().id(vId).build())
							.path(String.valueOf(filePath))
							.type(String.valueOf(fType))
							.imgSize(String.valueOf(fSize))
							.build());
					return ResponseDTO.of(HttpStatus.OK, SUCCESS_VEHICLE_RESISTER);
				}).orElseGet(() -> ResponseDTO.fail(HttpStatus.BAD_REQUEST, FAIL_VEHICLE_RESISTER));
	}

	@Transactional
	public ResponseDTO updateVehicle(VehicleUpdateDTO vehicleUpdateDTO, Long id, List<MultipartFile> files) {
		log.info(METHOD_NAME + "- updateVehicle");

		return Optional.of(new ResponseDTO())
				.filter(u -> id > 0L)
				.map(v -> vehicleRepository.findById(id))
				.filter(Optional::isPresent)
				.map(res -> {
					res.get().updateVehicle(vehicleUpdateDTO);
					return ResponseDTO.of(HttpStatus.OK, SUCCESS_VEHICLE_INFO_UPDATE);
				}).orElseGet(() -> ResponseDTO.fail(HttpStatus.BAD_REQUEST, FAIL_VEHICLE_INFO_UPDATE));
	}

	@Transactional
	public ResponseDTO deleteVehicle(Long id) {
		log.info(METHOD_NAME + "- deleteVehicle");

		return Optional.of(new ResponseDTO())
				.filter(u -> id >= 0L)
				.map(v -> vehicleRepository.findById(id))
				.map(res -> {
					if (res.isPresent()) vehicleRepository.deleteById(id);

					return (vehicleRepository.findById(id).isPresent()) ?
							ResponseDTO.fail(HttpStatus.BAD_REQUEST, FAIL_VEHICLE_INFO_DELETE) :
							ResponseDTO.of(HttpStatus.OK, SUCCESS_VEHICLE_INFO_DELETE);
				}).orElseGet(() -> ResponseDTO.fail(HttpStatus.BAD_REQUEST, FAIL_VEHICLE_INFO_DELETE));
	}
}
