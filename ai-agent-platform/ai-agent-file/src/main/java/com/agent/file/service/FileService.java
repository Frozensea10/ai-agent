package com.agent.file.service;

import com.agent.common.exception.BusinessException;
import com.agent.file.vo.FileDownloadVO;
import com.agent.file.vo.FileInfoVO;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件服务层，负责文件的上传、下载、删除、信息查询等核心操作。
 * <p>
 * 基于 MinIO 对象存储实现，统一处理存储桶的自动创建与常见业务异常。
 *
 * @author agent
 */
public interface FileService {

    /**
     * 上传文件到 MinIO。
     *
     * @param file 待上传的文件
     * @return 文件在存储桶中的对象名称
     * @throws BusinessException 当文件为空、超过大小限制或上传失败时抛出
     */
    String upload(MultipartFile file);

    /**
     * 从 MinIO 下载文件。
     *
     * @param objectName 文件对象名称
     * @return 文件输入流
     * @throws BusinessException 当文件不存在或下载失败时抛出
     */
    InputStream download(String objectName);

    /**
     * 获取文件的预签名访问地址。
     *
     * @param objectName 文件对象名称
     * @return 可直接访问的预览 URL，有效期 7 天
     * @throws BusinessException 当生成链接失败时抛出
     */
    String getPreviewUrl(String objectName);

    /**
     * 列出存储桶中所有文件的对象名称。
     *
     * @return 文件对象名称列表，最多返回 1000 条
     * @throws BusinessException 当查询失败时抛出
     */
    List<String> listObjects();

    /**
     * 查询文件列表，组装元数据与预览地址。
     *
     * @return 文件信息视图对象列表
     * @throws BusinessException 当对象名称列表查询失败时抛出
     */
    List<FileInfoVO> listFileInfos();

    /**
     * 下载文件并封装为视图对象，读取全部字节内容。
     *
     * @param objectName 文件对象名称
     * @return 包含文件字节、文件名与 Content-Type 的下载结果
     * @throws BusinessException 当对象名称为空、文件不存在或下载失败时抛出
     */
    FileDownloadVO downloadFile(String objectName);

    /**
     * 删除指定文件。
     *
     * @param objectName 文件对象名称
     * @throws BusinessException 当对象名称为空或删除失败时抛出
     */
    void deleteObject(String objectName);

    /**
     * 获取文件的元数据信息。
     *
     * @param objectName 文件对象名称
     * @return 包含对象名、原始文件名、上传时间、大小、Content-Type 等信息的 Map
     * @throws BusinessException 当对象名称为空或查询失败时抛出
     */
    Map<String, Object> getObjectInfo(String objectName);

    /**
     * 根据文件扩展名推断 Content-Type。
     *
     * @param objectName 文件对象名称
     * @return 对应的 MIME 类型，无法识别时返回 {@code application/octet-stream}
     */
    String detectContentType(String objectName);
}
