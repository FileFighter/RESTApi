package de.filefighter.rest.domain.filesystem.rest;

import de.filefighter.rest.domain.filesystem.data.dto.FileSystemItem;
import de.filefighter.rest.domain.filesystem.data.dto.FileSystemItemUpdate;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

public interface FileSystemRestServiceInterface {
    ResponseEntity<ArrayList<FileSystemItem>> getContentsOfFolderByPathAndAccessToken(String path, String accessToken);

    ResponseEntity<FileSystemItem> getInfoAboutFileOrFolderByIdAndAccessToken(long fsItemId, String accessToken);

    ResponseEntity<FileSystemItem> findFileOrFolderByNameAndAccessToken(String name, String accessToken);

    ResponseEntity<FileSystemItem> uploadFileSystemItemWithAccessToken(FileSystemItemUpdate fileSystemItemUpdate, String accessToken);

    ResponseEntity<FileSystemItem> updateFileSystemItemWithIdAndAccessToken(long fsItemId, FileSystemItemUpdate fileSystemItemUpdate, String accessToken);

    ResponseEntity<List<FileSystemItem>> deleteFileSystemItemWithIdAndAccessToken(long fsItemId, String accessToken);
}
