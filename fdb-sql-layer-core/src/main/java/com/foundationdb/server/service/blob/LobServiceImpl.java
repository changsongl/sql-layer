/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.foundationdb.server.service.blob;

import com.foundationdb.*;
import com.foundationdb.async.*;
import com.foundationdb.blob.BlobAsync;
import com.foundationdb.blob.BlobBase;
import com.foundationdb.directory.DirectorySubspace;
import com.foundationdb.server.service.Service;
import com.foundationdb.server.store.FDBHolder;
import com.foundationdb.subspace.Subspace;
import com.google.inject.*;

import java.util.*;

public class LobServiceImpl implements Service, LobService {
    private final DirectorySubspace lobDirectory;
    // update with configurable value
    private final String LOB_DIRECTORY = "lobs";

    @Inject
    public LobServiceImpl(FDBHolder fdbHolder) {
        this.lobDirectory = fdbHolder.getRootDirectory().createOrOpen(fdbHolder.getTransactionContext(), Arrays.asList(LOB_DIRECTORY)).get();
    }

    @Override
    public Future<DirectorySubspace> createLobSubspace(TransactionContext tcx, final List<String> path) {
        return lobDirectory.create(tcx, path);
    }

    @Override
    public Future<DirectorySubspace> getLobSubspace(TransactionContext tcx, final List<String> path) {
        return lobDirectory.open(tcx, path);
    }


    @Override
    public BlobBase getBlob(Subspace subspace) {
        // perform more test to verify it is a blob subspace, once clob is also initiated
        return new BlobBase(subspace);
    }

    @Override
    public Future<Void> removeLob(TransactionContext tcx, List<String> path) {
        return lobDirectory.remove(tcx, path); 
    }

    @Override
    public Future<DirectorySubspace> moveLob(TransactionContext tcx, final DirectorySubspace sourceSubspace, final List<String> targetPath) {
        List<String> newPath = new LinkedList<>(lobDirectory.getPath());
        newPath.addAll(targetPath);
        // check success, or handle failure when path already exists
        
        // ensure presence of parent directory for target path
        lobDirectory.createOrOpen(tcx, targetPath.subList(0, targetPath.size()-1)).get();
        return sourceSubspace.moveTo(tcx, newPath);
    }
    
    @Override
    public Future<Boolean> existsLob(TransactionContext tcx, final List<String> path){
        return lobDirectory.exists(tcx, path);
    }
    
    @Override
    public void runLobGarbageCollector(TransactionContext tcx) {
        List<String> lobs = lobDirectory.list(tcx).get();
        DirectorySubspace ds;
        BlobBase blob;
        if (lobs.size() > 0 ) {
            for (int i = 0; i < lobs.size(); i++) {
                String lob = lobs.get(i);
                ds = lobDirectory.open(tcx, Arrays.asList(lob)).get();
                blob = getBlob(ds);
                if (!blob.isLinked(tcx).get()) {
                    removeLob(tcx, Arrays.asList(lob)).get();
                }
            }
        }
    }
    
    
    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void crash() {
        stop();
    }
}
