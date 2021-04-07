package de.filefighter.rest.domain.filesystem.business;

import de.filefighter.rest.domain.common.exceptions.FileFighterDataException;
import de.filefighter.rest.domain.filesystem.data.InteractionType;
import de.filefighter.rest.domain.filesystem.data.dto.FileSystemItem;
import de.filefighter.rest.domain.filesystem.data.persistence.FileSystemEntity;
import de.filefighter.rest.domain.filesystem.data.persistence.FileSystemRepository;
import de.filefighter.rest.domain.filesystem.exceptions.FileSystemContentsNotAccessibleException;
import de.filefighter.rest.domain.filesystem.exceptions.FileSystemItemCouldNotBeDeletedException;
import de.filefighter.rest.domain.filesystem.exceptions.FileSystemItemNotFoundException;
import de.filefighter.rest.domain.filesystem.type.FileSystemType;
import de.filefighter.rest.domain.filesystem.type.FileSystemTypeRepository;
import de.filefighter.rest.domain.user.business.UserBusinessService;
import de.filefighter.rest.domain.user.data.dto.User;
import de.filefighter.rest.domain.user.exceptions.UserNotFoundException;
import de.filefighter.rest.domain.user.group.Group;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

@Log4j2
@Service
public class FileSystemBusinessService {

    private final FileSystemRepository fileSystemRepository;
    private final FileSystemHelperService fileSystemHelperService;
    private final FileSystemTypeRepository fileSystemTypeRepository;
    private final UserBusinessService userBusinessService;
    private final MongoTemplate mongoTemplate;

    public static final String DELETION_FAILED_MSG = "Failed to delete FileSystemEntity with id ";

    public FileSystemBusinessService(FileSystemRepository fileSystemRepository, FileSystemHelperService fileSystemHelperService, FileSystemTypeRepository fileSystemTypeRepository, UserBusinessService userBusinessService, MongoTemplate mongoTemplate) {
        this.fileSystemRepository = fileSystemRepository;
        this.fileSystemHelperService = fileSystemHelperService;
        this.fileSystemTypeRepository = fileSystemTypeRepository;
        this.userBusinessService = userBusinessService;
        this.mongoTemplate = mongoTemplate;
    }

    @SuppressWarnings("java:S3776")
    public List<FileSystemItem> getFolderContentsByPath(String path, User authenticatedUser) {
        String[] pathWithoutSlashes = path.split("/");

        String pathToFind;
        User ownerOfRequestedFolder = null;

        if (path.equals("/")) {
            pathToFind = "/";
        } else {
            if (pathWithoutSlashes.length < 2)
                throw new FileSystemContentsNotAccessibleException("Path was in wrong format.");

            if (!"".equals(pathWithoutSlashes[0]))
                throw new FileSystemContentsNotAccessibleException("Path was in wrong format. Use a leading backslash.");

            // the first path must be the the username.
            try {
                ownerOfRequestedFolder = userBusinessService.findUserByUsername(pathWithoutSlashes[1]);
                String[] fileSystemPath = path.split(ownerOfRequestedFolder.getUsername());
                if (fileSystemPath.length == 1) {
                    if (!fileSystemPath[0].equals("/"))
                        throw new FileSystemContentsNotAccessibleException();

                    pathToFind = "/";
                } else {
                    pathToFind = fileSystemPath[1];
                }
            } catch (UserNotFoundException exception) {
                throw new FileSystemContentsNotAccessibleException();
            }
        }

        pathToFind = fileSystemHelperService.removeTrailingBackSlashes(pathToFind).toLowerCase();

        // find the folder with matching path.
        ArrayList<FileSystemEntity> listOfPossibleDirectories = fileSystemRepository.findByPath(pathToFind);
        if (null == listOfPossibleDirectories) // does return null and not a empty collection.
            throw new FileSystemContentsNotAccessibleException();

        // remove all not accessible items.
        // this is only the case if the real / was requested. -> filter by visibility
        boolean actualRootWasRequested = null == ownerOfRequestedFolder;
        if (actualRootWasRequested) {
            listOfPossibleDirectories.removeIf(entity -> entity.isFile() || entity.getTypeId() != FileSystemType.FOLDER.getId() || !fileSystemHelperService.userIsAllowedToInteractWithFileSystemEntity(entity, authenticatedUser, InteractionType.READ));

            // do not get the actual contents here but display the folder names as a fake directory.
            ArrayList<FileSystemItem> fileSystemItems = new ArrayList<>();
            for (FileSystemEntity folder : listOfPossibleDirectories) {
                // change names here accordingly.
                fileSystemItems.add(fileSystemHelperService.createDTO(folder, authenticatedUser, "/"));
            }

            return fileSystemItems;
        } else {
            User finalOwnerOfRequestedFolder = ownerOfRequestedFolder;
            listOfPossibleDirectories.removeIf(entity -> entity.isFile() || entity.getTypeId() != FileSystemType.FOLDER.getId() || entity.getOwnerId() != finalOwnerOfRequestedFolder.getUserId());

            if (listOfPossibleDirectories.isEmpty())
                throw new FileSystemContentsNotAccessibleException();

            // now one Folder should remain
            if (listOfPossibleDirectories.size() != 1)
                throw new FileFighterDataException("Found more than one folder with the path " + pathToFind);

            // check if the autheticatedUser can access this.
            if (!fileSystemHelperService.userIsAllowedToInteractWithFileSystemEntity(listOfPossibleDirectories.get(0), authenticatedUser, InteractionType.READ))
                throw new FileSystemContentsNotAccessibleException();

            ArrayList<FileSystemItem> fileSystemItems = new ArrayList<>();
            ArrayList<FileSystemEntity> folderContents =
                    (ArrayList<FileSystemEntity>) fileSystemHelperService.getFolderContentsOfEntityAndPermissions(listOfPossibleDirectories.get(0), authenticatedUser, true, false);

            for (FileSystemEntity fileSystemEntityInFolder : folderContents) {
                fileSystemItems.add(fileSystemHelperService.createDTO(fileSystemEntityInFolder, authenticatedUser, "/" + ownerOfRequestedFolder.getUsername() + pathToFind));
            }

            return fileSystemItems;
        }
    }

    public FileSystemItem getFileSystemItemInfo(long fsItemId, User authenticatedUser) {
        FileSystemEntity fileSystemEntity = fileSystemRepository.findByFileSystemId(fsItemId);
        if (null == fileSystemEntity)
            throw new FileSystemItemNotFoundException(fsItemId);

        if (!fileSystemHelperService.userIsAllowedToInteractWithFileSystemEntity(fileSystemEntity, authenticatedUser, InteractionType.READ))
            throw new FileSystemItemNotFoundException(fsItemId);

        return fileSystemHelperService.createDTO(fileSystemEntity, authenticatedUser, null);
    }

    public List<FileSystemItem> deleteFileSystemItemById(long fsItemId, User authenticatedUser) {
        FileSystemEntity fileSystemEntity = fileSystemRepository.findByFileSystemId(fsItemId);
        if (null == fileSystemEntity)
            throw new FileSystemItemCouldNotBeDeletedException(fsItemId);

        if (!(fileSystemHelperService.userIsAllowedToInteractWithFileSystemEntity(fileSystemEntity, authenticatedUser, InteractionType.READ) && fileSystemHelperService.userIsAllowedToInteractWithFileSystemEntity(fileSystemEntity, authenticatedUser, InteractionType.DELETE)))
            throw new FileSystemItemCouldNotBeDeletedException(fsItemId);

        return deleteFileSystemEntity(fileSystemEntity, authenticatedUser);
    }

    @SuppressWarnings({"squid:S3776", "squid:S2142"})
    public List<FileSystemItem> deleteFileSystemEntity(FileSystemEntity parentEntity, User authenticatedUser) {
        ArrayList<FileSystemItem> returnList = new ArrayList<>();
        try {
            LinkedBlockingQueue<FileSystemEntity> queue = new LinkedBlockingQueue<>();
            queue.put(parentEntity);

            FileSystemEntity currentEntity;
            do {
                currentEntity = queue.poll();
                if (null == currentEntity)
                    throw new FileFighterDataException("Queue was empty.");

                if (currentEntity.isFile() && fileSystemTypeRepository.findFileSystemTypeById(currentEntity.getTypeId()) != FileSystemType.FOLDER) {
                    log.info("Found file to delete: {}.", currentEntity);
                    fileSystemHelperService.deleteAndUnbindFileSystemEntity(currentEntity);
                    returnList.add(fileSystemHelperService.createDTO(currentEntity, authenticatedUser, null));
                } else {
                    boolean foundNonDeletable = false;
                    boolean foundInvisible = false;
                    if (currentEntity.getItemIds().length != 0) {
                        List<FileSystemEntity> items = fileSystemHelperService.getFolderContentsOfEntityAndPermissions(currentEntity, authenticatedUser, false, false);
                        for (FileSystemEntity item : items) {
                            if (fileSystemHelperService.userIsAllowedToInteractWithFileSystemEntity(item, authenticatedUser, InteractionType.READ)) {
                                if (fileSystemHelperService.userIsAllowedToInteractWithFileSystemEntity(item, authenticatedUser, InteractionType.DELETE)) {
                                    queue.put(item);
                                } else {
                                    // a entity could not be removed disable the deletion of the parent folder. (current Entity)
                                    foundNonDeletable = true;
                                }
                            } else {
                                // the entity also cannot be deleted BUT the current User looses the permissions.
                                foundInvisible = true;
                            }
                        }

                        log.info("Currently working on: {}.", currentEntity);

                        if (foundInvisible && !foundNonDeletable) {
                            // only invisible files left.
                            log.info("Found invisible FileSystemEntity {}", currentEntity);

                            Query query = new Query().addCriteria(Criteria.where("fileSystemId").is(currentEntity.getFileSystemId()));
                            Update newUpdate = new Update();

                            // user is either directly in the visible ids or in a group that is visible.
                            long[] newIdsWithoutCurrentUserId = Arrays.stream(currentEntity.getVisibleForUserIds()).filter(userId -> userId != authenticatedUser.getUserId()).toArray();
                            if (newIdsWithoutCurrentUserId.length != currentEntity.getVisibleForUserIds().length) {
                                // apply it.
                                newUpdate.set("visibleForUserIds", newIdsWithoutCurrentUserId);
                            }

                            // or user is in a group that can see the filesystem entity.
                            long[] newGroupIds = currentEntity.getVisibleForGroupIds();
                            if (newGroupIds.length != 0) {
                                for (Group group : authenticatedUser.getGroups()) {
                                    newGroupIds = Arrays.stream(newGroupIds).filter(id -> id != group.getGroupId()).toArray();
                                }
                                newUpdate.set("visibleForGroupIds", newGroupIds);
                            }
                            mongoTemplate.findAndModify(query, newUpdate, FileSystemEntity.class);
                        } else if (!foundInvisible && !foundNonDeletable) {
                            // every child item of the entity can be deleted.
                            log.info("Found no invisible or non deletable FileSystemEntities.");
                            fileSystemHelperService.deleteAndUnbindFileSystemEntity(currentEntity);
                            returnList.add(fileSystemHelperService.createDTO(currentEntity, authenticatedUser, null));
                        } else {
                            // else some files are left. invisible or not. but the entity cannot be deleted.
                            log.info("Some visible entites could not be deleted.");
                        }
                    } else {
                        fileSystemHelperService.deleteAndUnbindFileSystemEntity(currentEntity);
                        returnList.add(fileSystemHelperService.createDTO(currentEntity, authenticatedUser, null));
                    }
                }
            } while (!queue.isEmpty());
        } catch (InterruptedException ex) {
            log.error(ex);
            throw new FileFighterDataException(ex.getMessage());
        }

        // update the time stamps in the file tree
        fileSystemHelperService.recursivlyUpdateTimeStamps(parentEntity, authenticatedUser, fileSystemHelperService.getCurrentTimeStamp());

        return returnList;
    }
}
