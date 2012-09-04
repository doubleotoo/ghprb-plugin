/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.janinko.ghprb;

import hudson.model.AbstractBuild;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author jbrazdil
 */
public class GhprbBuild {
	QueueTaskFuture<?> future;
	AbstractBuild<?,?> build;
	GhprbRepo repo;
	int pull;
	boolean merge;
	
	GhprbBuild(GhprbRepo repo, int pull, QueueTaskFuture<?> future, boolean merge){
		this.repo = repo;
		this.pull =  pull;
		this.future = future;
		this.build = null;
		this.merge = merge;
	}
	
	public boolean check() {
		if (build == null && future.getStartCondition().isDone()) {
			try {
				build = (AbstractBuild<?, ?>) future.getStartCondition().get();
				repo.addComment(pull, (merge ? "Merge build started" : "Build started: ") + Jenkins.getInstance().getRootUrl() + build.getUrl());
			} catch (Exception ex) {
				Logger.getLogger(GhprbBuild.class.getName()).log(Level.SEVERE, null, ex);
			}
		}else if (build != null && future.isDone()){
			try {
				repo.addComment(pull, (merge ? "Merge build finished: **" : "Build finished: **") + build.getResult() + "** " + Jenkins.getInstance().getRootUrl() + build.getUrl() );
				return true;
			} catch (IOException ex) {
				Logger.getLogger(GhprbBuild.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		return false;
	}
	
	public boolean cancel() {
		if(build == null){
			try {
				build = (AbstractBuild<?, ?>) future.waitForStart();
			} catch (Exception ex) {
				Logger.getLogger(GhprbBuild.class.getName()).log(Level.WARNING, null, ex);
				return false;
			}
		}
		if(build.getExecutor() == null) return false;
		
		build.getExecutor().interrupt();
		return true;
	}

	@Override
	public int hashCode() {
		int hash = 3;
		hash = 71 * hash + this.pull;
		hash = 71 * hash + (this.merge ? 1 : 0);
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final GhprbBuild other = (GhprbBuild) obj;
		if (this.pull != other.pull) {
			return false;
		}
		if (this.merge != other.merge) {
			return false;
		}
		return true;
	}

	public int getPullID() {
		return pull;
	}

	public boolean isMerge() {
		return merge;
	}
}
