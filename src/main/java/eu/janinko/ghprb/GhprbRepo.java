/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.janinko.ghprb;

import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

/**
 *
 * @author jbrazdil
 */
public class GhprbRepo {
	private GhprbTrigger trigger;
	private GitHub gh;
	private GHRepository repo;
	private Pattern retestPhrasePattern;
	private Pattern whitelistPhrasePattern;
	private String reponame;
	private HashSet<GhprbBuild> builds;
	
	public GhprbRepo(GhprbTrigger trigger, String user, String repository){
		this.trigger = trigger;
		reponame = user + "/" + repository;
		System.out.println("The reponame is " + reponame);
		gh = GitHub.connect(trigger.getDescriptor().getUsername(), null, trigger.getDescriptor().getPassword());
		retestPhrasePattern = Pattern.compile(trigger.DESCRIPTOR.getRetestPhrase());
		whitelistPhrasePattern = Pattern.compile(trigger.DESCRIPTOR.getWhitelistPhrase());
		builds = new HashSet<GhprbBuild>();
	}
	
	public void check(Map<Integer,GhprbPullRequest> pulls) throws IOException{
		if(repo == null) repo = gh.getRepository(reponame);
		List<GHPullRequest> prs = repo.getPullRequests(GHIssueState.OPEN);
		Set<Integer> closedPulls = new HashSet<Integer>(pulls.keySet());

		for(GHPullRequest pr : prs){
			Integer id = pr.getNumber();
			GhprbPullRequest pull;
			if(pulls.containsKey(id)){
				pull = pulls.get(id);
			}else{
				pull = new GhprbPullRequest(pr);
				pulls.put(id, pull);
			}
			pull.check(pr,this);
			closedPulls.remove(id);
		}
		
		removeClosed(closedPulls, pulls);
		checkBuilds();
	}
	
	private void removeClosed(Set<Integer> closedPulls, Map<Integer,GhprbPullRequest> pulls) throws IOException {
		if(closedPulls.isEmpty()) return;
		
		for(Integer id : closedPulls){
			GHPullRequest pr = repo.getPullRequest(id);
			pulls.remove(id);
		}
	}
	
	private void checkBuilds() throws IOException{
		Iterator<GhprbBuild> it;
		it = builds.iterator();
		while(it.hasNext()){
			GhprbBuild build = it.next();
			if(build.check()){
				it.remove();
			}
		}
	}

	public boolean cancelBuild(int id) {
		boolean ret = false;
		Iterator<GhprbBuild> it;
		it = builds.iterator();
		while(it.hasNext()){
			GhprbBuild build  = it.next();
			if(build.getPullID() == id){
				if(build.cancel()){
					it.remove();
					ret = true;
				}
			}
		}
		return ret;
	}
	
	public boolean isWhitelisted(String username){
		return trigger.whitelisted.contains(username) || trigger.admins.contains(username);
	}
	
	public boolean isAdmin(String username){
		return trigger.admins.contains(username);
	}
	
	public boolean isRetestPhrase(String comment){
		return retestPhrasePattern.matcher(comment).matches();
	}
	
	public boolean isWhitelistPhrase(String comment){
		return whitelistPhrasePattern.matcher(comment).matches();
	}
	
	public void addComment(int id, String comment) throws IOException{
		repo.getPullRequest(id).comment(comment);
	}

	public void addWhitelist(String author) {
		trigger.whitelist = trigger.whitelist + " " + author;
		trigger.whitelisted.add(author);
		trigger.changed = true;
	}
	
	public boolean isMe(String username){
		return trigger.getDescriptor().getUsername().equals(username);
	}
	
	public void startJob(int id, String commit){
		QueueTaskFuture<?> build = trigger.startJob(new GhprbCause(commit, id));
		if(build == null){
			System.out.println("WUUUT?!!");
			return;
		}
		builds.add(new GhprbBuild(this, id, build, false));
	}
	
	public void startMergeJob(int id){
		QueueTaskFuture<?> build = trigger.startJob(new GhprbCause("**/pr/1/merge", id));
		if(build == null){
			System.out.println("WUUUT?!!");
			return;
		}
		builds.add(new GhprbBuild(this, id, build, false));
	}
}
