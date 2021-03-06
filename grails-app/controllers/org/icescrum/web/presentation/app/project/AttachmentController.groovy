/*
* Copyright (c) 2014 Kagilum.
*
* This file is part of iceScrum.
*
* iceScrum is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License as published by
* the Free Software Foundation, either version 3 of the License.
*
* iceScrum is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Affero General Public License
* along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
*
* Authors:
*
* Vincent Barrier (vbarrier@kagilum.com)
*/
package org.icescrum.web.presentation.app.project

import grails.converters.JSON
import grails.plugins.springsecurity.Secured
import org.apache.commons.io.FilenameUtils
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Task
import org.icescrum.core.event.IceScrumEventType

import javax.servlet.http.HttpServletResponse

/**
 * Created by vbarrier on 03/04/2014.
 */
class AttachmentController {

    def springSecurityService
    def attachmentableService

    def show = {
        def attachmentable = attachmentableObject
        if (attachmentable) {
            def attachment = attachmentable.attachments?.find { it.id == params.long('id') }
            if (attachment) {
                if (attachment.url) {
                    redirect(url: "${attachment.url}")
                    return
                } else {
                    File file = attachmentableService.getFile(attachment)

                    if (file.exists()) {
                        String filename = attachment.filename
                        ['Content-disposition': "attachment;filename=\"$filename\"", 'Cache-Control': 'private', 'Pragma': ''].each { k, v ->
                            response.setHeader(k, v)
                        }
                        response.contentType = attachment.contentType
                        response.outputStream << file.newInputStream()
                        return
                    }
                }
            }
        }
        response.status = HttpServletResponse.SC_NOT_FOUND
    }

    @Secured('isAuthenticated() and stakeHolder()')
    def list = {
        def attachmentable = attachmentableObject
        if (attachmentable) {
            withFormat {
                html { render(status: 200, contentType: 'application/json', text: attachmentable.attachments as JSON) }
                json { renderRESTJSON(text:attachmentable.attachments) }
                xml  { renderRESTXML(text:attachmentable.attachments) }
            }
        } else {
            returnError(text:message(code: 'todo.is.ui.backlogelement.attachments.error'))
        }
    }

    @Secured('isAuthenticated() and stakeHolder()')
    def save = {
        def attachmentable = attachmentableObject
        if (attachmentable) {
            def upfile = params.file ?: request.getFile('file')
            def filename = FilenameUtils.getBaseName(upfile.originalFilename?:upfile.name)
            def ext = FilenameUtils.getExtension(upfile.originalFilename?:upfile.name)
            def tmpF = null
            if(upfile.originalFilename){
                tmpF = session.createTempFile(filename, '.' + ext)
                request.getFile("file").transferTo(tmpF)
            }
            grailsApplication.mainContext[params.type+'Service'].publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, attachmentable, ['addAttachment':null])
            attachmentable.addAttachment(springSecurityService.currentUser, tmpF?: upfile, upfile.originalFilename?:upfile.name)
            def attachment = attachmentable.attachments.first()
            grailsApplication.mainContext[params.type+'Service'].publishSynchronousEvent(IceScrumEventType.UPDATE, attachmentable, ['addedAttachment':attachment])
            render(status: 200, contentType: 'application/json', text: [
                    id      : attachment.id,
                    filename: attachment.inputName,
                    ext     : ext,
                    size    : attachment.length,
                    provider: attachment.provider ?: ''] as JSON)
        }
    }

    def delete = {
        def attachmentable = attachmentableObject
        if (attachmentable) {
            def attachment = attachmentable.attachments?.find{ it.id == params.long('id') }
            if (attachment){
                grailsApplication.mainContext[params.type+'Service'].publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, attachmentable, ['removeAttachment':attachment])
                attachmentable.removeAttachment(attachment)
                grailsApplication.mainContext[params.type+'Service'].publishSynchronousEvent(IceScrumEventType.UPDATE, attachmentable, ['removedAttachment':null])
                withFormat {
                    html { render(status: 200) }
                    json { renderRESTJSON(status: 204) }
                    xml  { renderRESTXML(status: 204) }
                }
            }
        }
    }

    private getAttachmentableObject(){
        def attachmentable
        switch (params.type){
            case 'story':
                attachmentable = Story.getInProduct(params.long('product'),params.long('attachmentable')).list()
                break
            case 'task':
                attachmentable = Task.getInProduct(params.long('product'),params.long('attachmentable'))
                break
            default:
                attachmentable = null
        }
        attachmentable
    }

}
