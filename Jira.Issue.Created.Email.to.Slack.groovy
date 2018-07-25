import com.atlassian.mail.Email;
import com.atlassian.mail.server.MailServerManager;
import com.atlassian.mail.server.SMTPMailServer;
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.AttachmentManager; 
import com.atlassian.jira.issue.attachment.Attachment;
import com.atlassian.jira.util.AttachmentUtils; 
import com.atlassian.jira.util.PathUtils;
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMultipart
import javax.mail.internet.MimeUtility
import com.atlassian.jira.issue.RendererManager
import javax.activation.DataHandler
import javax.activation.FileDataSource
import com.atlassian.jira.util.io.InputStreamConsumer
import org.springframework.util.StreamUtils;
import org.apache.commons.io.FilenameUtils

//A filestream class to create a file object based on the issue attachment
public class FileInputStreamConsumer implements InputStreamConsumer<File>{
	
	private final String filename;
	public FileInputStreamConsumer(String filename) {
		this.filename = filename;
	}
	@Override
	public File withInputStream(InputStream is) throws IOException {
		final File f = File.createTempFile(FilenameUtils.getBaseName(filename), FilenameUtils.getExtension(filename));
		StreamUtils.copy(is, new FileOutputStream(f));
		return f;
	}
}


//set variables
def issue = issueManager.getIssueObject(issueKey)
def reporter = issue.reporter.getDisplayName()
def subject = "${issue.summary} submitted by ${issue.reporter.displayName}"

//If no message, set default message.
def message
if(issue.description){
	message = issue.description
}
else
{
	message = "No Description"
}

//Get custom user fields
def location = issue.getCustomFieldValue(com.atlassian.jira.component.ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_10322"))
def phone = issue.getCustomFieldValue(com.atlassian.jira.component.ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_10323"))
def title = issue.getCustomFieldValue(com.atlassian.jira.component.ComponentAccessor.getCustomFieldManager().getCustomFieldObject("customfield_10400"))

//Render Markdown as HTML
def rendererManager = ComponentAccessor.getComponent(RendererManager.class)
def fieldLayoutItem = ComponentAccessor.getFieldLayoutManager().getFieldLayout(issue).getFieldLayoutItem("comment")
def renderer = rendererManager.getRendererForField(fieldLayoutItem)
message =  renderer.render(message, null)

//Set email body content
def body = "<a href='https://YOUR.JIRA.URL/browse/$issue.key'>${issue.key} has been created</a><br><br><b>Client Information</b><br><b>Name: </b> ${issue.reporter.displayName}<br><b>Email: </b> ${issue.reporter.emailAddress}<br><b>Title: </b> ${title}<br><b>Location: </b> ${location}<br><b>Phone: </b> ${phone}<br><br><b>Message:</b><br>${message}"

//Set Email address and MimeType
def emailAddr = "YOUR.SLACK.EMAIL"
def mimeType = "text/html"

//Get Mailserver and create email object
def mailServer = ComponentAccessor.getMailServerManager().getDefaultSMTPMailServer()
if (mailServer) {
    def email = new Email(emailAddr);
    email.setSubject(subject);

//Create Main Multipart
    def mainMultiPart = new MimeMultipart("mixed")
    
// Create html BodyPart that will be added to MainMultipart
    def htmlBodyPart = new MimeBodyPart();
    htmlBodyPart.setContent((String)body, "text/html; charset=utf-8");
    mainMultiPart.addBodyPart(htmlBodyPart);
    
//Loop through Attachments and create a new BodyPart for each one. Attach each BodyPart to MainMultiPart
	def attachmentManager = ComponentAccessor.getAttachmentManager()    
    List<Attachment> attachments = attachmentManager.getAttachments(issue);
    def NumOfAttachedfiles = attachments.size();
    File attachedFile = null
    def attachedFileName = ""
    if(!attachments.isEmpty()) {
        def fileCreatedDate, fileCreatedDateFinal;
        Attachment attachmentFinal;
        
        for (int i=0; i< NumOfAttachedfiles; i++) {
            def attachBody = new MimeBodyPart();
            Attachment attachment = attachments[i];
            fileCreatedDate = attachment.getCreated();
            attachedFileName = attachment.getFilename();
            attachedFile = attachmentManager.streamAttachmentContent(attachment, new FileInputStreamConsumer(attachedFileName))
            FileDataSource source = new FileDataSource(attachedFile);
        	attachBody.setDataHandler(new DataHandler(source));
        	attachBody.setFileName(attachedFileName);
        	mainMultiPart.addBodyPart(attachBody);
         }
}

//Send the email
	email.setMultipart(mainMultiPart)
	mailServer.send(email);


  } else {
// Problem getting the mail server from JIRA configuration, log this error
    log.warn("No SMTP email server found")
  }
