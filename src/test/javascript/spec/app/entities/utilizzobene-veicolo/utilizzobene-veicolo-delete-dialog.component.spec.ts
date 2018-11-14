/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ParcoautoTestModule } from '../../../test.module';
import { UtilizzobeneVeicoloDeleteDialogComponent } from 'app/entities/utilizzobene-veicolo/utilizzobene-veicolo-delete-dialog.component';
import { UtilizzobeneVeicoloService } from 'app/entities/utilizzobene-veicolo/utilizzobene-veicolo.service';

describe('Component Tests', () => {
    describe('UtilizzobeneVeicolo Management Delete Component', () => {
        let comp: UtilizzobeneVeicoloDeleteDialogComponent;
        let fixture: ComponentFixture<UtilizzobeneVeicoloDeleteDialogComponent>;
        let service: UtilizzobeneVeicoloService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ParcoautoTestModule],
                declarations: [UtilizzobeneVeicoloDeleteDialogComponent]
            })
                .overrideTemplate(UtilizzobeneVeicoloDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(UtilizzobeneVeicoloDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(UtilizzobeneVeicoloService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete', inject(
                [],
                fakeAsync(() => {
                    // GIVEN
                    spyOn(service, 'delete').and.returnValue(of({}));

                    // WHEN
                    comp.confirmDelete(123);
                    tick();

                    // THEN
                    expect(service.delete).toHaveBeenCalledWith(123);
                    expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                })
            ));
        });
    });
});
