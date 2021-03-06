/*
 * Power by www.xiaoi.com
 */
package com.eastrobot.web;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Calendar;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.artofsolving.jodconverter.OfficeDocumentConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.eastrobot.config.WebappContext;
import com.eastrobot.util.HtmlUtils;

/**
 * @author <a href="mailto:eko.z@outlook.com">eko.zhan</a>
 * @date 2017年7月29日 上午9:44:36
 * @version 1.0
 */
@RequestMapping("index")
@RestController
public class IndexController {
	
	private final static Logger logger = LoggerFactory.getLogger(IndexController.class);
	
	private final String outputExtension = "html";

	/**
	 * 文件列表
	 * @author eko.zhan at 2017年8月9日 下午8:32:19
	 * @return
	 * @throws FileNotFoundException
	 */
	@RequestMapping("getDataList")
	public JSONArray getDataList() throws FileNotFoundException{
		JSONArray arr = new JSONArray();
		File dir = ResourceUtils.getFile("classpath:static/DATAS");
		File[] files = dir.listFiles();
		for (File file : files){
			if (file.isFile()){
				JSONObject json = new JSONObject();
				json.put("path", file.getPath());
				json.put("name", file.getName());
				json.put("size", file.length());
				arr.add(json);
			}
		}
		return arr;
	}
	
	/**
	 * 加载文件
	 * @author eko.zhan at 2017年8月9日 下午8:32:30
	 * @param name
	 * @param request
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	@RequestMapping(value="loadFileData", produces="text/plain;charset=utf-8")
	public String loadFileData(String name, HttpServletRequest request) throws FileNotFoundException, IOException{
		File file = ResourceUtils.getFile("classpath:static/DATAS/" + name);
		String basename = FilenameUtils.getBaseName(file.getName());
		File targetFile = new File(file.getParent() + "/" + basename + "/" + basename + "." + outputExtension);
		if (targetFile.exists()){
			String data = IOUtils.toString(new FileInputStream(targetFile), HtmlUtils.getFileEncoding(targetFile));
			//TODO 如果是网络图片，如何处理？
			//TODO 保存后再次打开html文档，如何处理？
			data = HtmlUtils.replaceHtmlTag(data, "img", "src", "src=\"" + request.getContextPath() + "/index/loadFileImg?name=" + name + "&imgname=", "\"");
			return data;
		}
		return "";
	}
	
	/**
	 * 加载 html 中的图片资源
	 * @author eko.zhan at 2017年8月9日 下午8:32:06
	 * @param name
	 * @param imgname
	 * @return
	 * @throws IOException
	 */
	@RequestMapping("loadFileImg")
	public ResponseEntity<byte[]> loadFileImg(String name, String imgname){
		try {
			String basename = FilenameUtils.getBaseName(name);
			File file = ResourceUtils.getFile("classpath:static/DATAS/" + basename + "/" + imgname);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.IMAGE_PNG);
			return new ResponseEntity<byte[]>(IOUtils.toByteArray(new FileInputStream(file)), headers, HttpStatus.OK);
		} catch (FileNotFoundException e) {
			logger.error("文件[" + name + "]不存在, " + e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		} 
		return null;
	}
	/**
	 * 上传文件
	 * @author eko.zhan at 2017年8月9日 下午8:32:39
	 * @param uploadFile
	 * @param request
	 * @return
	 */
	@RequestMapping("uploadData")
	public JSONObject uploadData(@RequestParam("uploadFile") MultipartFile uploadFile, HttpServletRequest request){
		JSONObject json = new JSONObject();
		json.put("result", 1);
		WebappContext webappContext = WebappContext.get(request.getServletContext());
		OfficeDocumentConverter converter = webappContext.getDocumentConverter();
		
		try {
			File targetDir = ResourceUtils.getFile("classpath:static/DATAS/");
			String inputExtension = FilenameUtils.getExtension(uploadFile.getOriginalFilename());
			String inputFilename = String.valueOf(Calendar.getInstance().getTimeInMillis());
			File file = new File(targetDir.getAbsolutePath() + "/" + inputFilename + "." + inputExtension);
			FileCopyUtils.copy(uploadFile.getBytes(), file);
			
			File outputFile = new File(targetDir.getAbsolutePath() + "/" + inputFilename + "/" + inputFilename + "." + outputExtension);
			try {
	        	long startTime = System.currentTimeMillis();
	        	converter.convert(file, outputFile);
	        	long conversionTime = System.currentTimeMillis() - startTime;
	        	logger.info(String.format("successful conversion: %s [%db] to %s in %dms", inputExtension, file.length(), outputExtension, conversionTime));
	        } catch (Exception e) {
	            logger.error(String.format("failed conversion: %s [%db] to %s; %s; input file: %s", inputExtension, file.length(), outputExtension, e, file.getName()));
	        }
			
//			File newFile = new File(targetDir.getAbsolutePath() + "/" + inputFilename + ".ekoz." + inputExtension);
//			converter.convert(outputFile, newFile);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			json.put("result", 0);
		} catch (IOException e) {
			e.printStackTrace();
			json.put("result", 0);
		} 
		return json;
	}
	
	/**
	 * 保存html内容
	 * @author eko.zhan at 2017年8月9日 下午9:04:20
	 * @param name
	 * @param data
	 * @throws IOException 
	 */
	@RequestMapping("saveFileData")
	public JSONObject saveFileData(String name, String data, HttpServletRequest request){
		//TODO 这是一个伪保存，只是修改了 HTML 内容，并未修改 file 文件，如果用户单击下载，依然会存在问题
		//TODO 如果用户修改了图片，如何处理？
		JSONObject json = new JSONObject();
		json.put("result", 1);
		try {
			File file = ResourceUtils.getFile("classpath:static/DATAS/" + name);
			String basename = FilenameUtils.getBaseName(file.getName());
			File targetFile = new File(file.getParent() + "/" + basename + "/" + basename + "." + outputExtension);
			if (targetFile.exists()){
				//将html中的body内容替换为当前 data 数据
				//String htmlData = IOUtils.toString(new FileInputStream(targetFile), HtmlUtils.getFileEncoding(targetFile));
				String htmlData = HtmlUtils.HEAD_TEMPLATE + data + HtmlUtils.FOOT_TEMPLATE;
				//TODO 如何处理文件编码？保存后尽管通过请求能访问中文内容，但是直接磁盘双击html文件显示乱码
				//add by eko.zhan at 2017-08-11 14:55 解决方案：重写Html头，编码修改为 utf-8
				IOUtils.write(htmlData.getBytes(HtmlUtils.UTF8), new FileOutputStream(targetFile));
				//TODO 由于html文件编码不正确，导致转换成word后文件编码也不正确
				//add by eko.zhan at 2017-08-11 15:05 上面处理了html编码后，转换的编码问题也相应解决了
				//TODO 由html转换成doc会导致doc样式有误
				WebappContext webappContext = WebappContext.get(request.getServletContext());
				OfficeDocumentConverter converter = webappContext.getDocumentConverter();
				try {
		        	long startTime = System.currentTimeMillis();
		        	converter.convert(targetFile, file);
		        	long conversionTime = System.currentTimeMillis() - startTime;
		        	logger.info(String.format("successful conversion: %s [%db] to %s in %dms", FilenameUtils.getExtension(targetFile.getName()), file.length(), outputExtension, conversionTime));
		        } catch (Exception e) {
		            logger.error(String.format("failed conversion: %s [%db] to %s; %s; input file: %s", FilenameUtils.getExtension(file.getName()), file.length(), outputExtension, e, file.getName()));
		        }
			}
		} catch (FileNotFoundException e) {
			json.put("result", 0);
			e.printStackTrace();
		} catch (IOException e) {
			json.put("result", 0);
			e.printStackTrace();
		}
		return json;
	}
	
	/**
	 * 删除文件
	 * @author eko.zhan at 2017年8月9日 下午9:32:18
	 * @param name
	 * @return
	 */
	@RequestMapping("delete")
	public JSONObject delete(String name){
		//TODO windows操作系统上如果html文件被占用则无法删除，是否可以用 File.creteTempFile 来解决？
		JSONObject json = new JSONObject();
		json.put("result", 1);
		try {
			File file = ResourceUtils.getFile("classpath:static/DATAS/" + name);
			String basename = FilenameUtils.getBaseName(file.getName());
			File targetDir = new File(file.getParent() + "/" + basename);
			if (targetDir.exists()){
				FileUtils.deleteDirectory(targetDir);
			}
			FileUtils.forceDelete(file);
		} catch (FileNotFoundException e) {
			json.put("result", 0);
			json.put("msg", e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			json.put("result", 0);
			json.put("msg", e.getMessage());
			e.printStackTrace();
		}
		return json;
	}
}
