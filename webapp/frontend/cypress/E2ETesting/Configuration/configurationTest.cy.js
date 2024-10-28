/**
 * Előfeltétel: Sikeres bejelentkezés
 * Leírás: Egy alap konfiguráció végig vitele
 * 
 */



describe('Configuration test',()=>{

    it('C01 - Base configuration',()=>{
        cy.visit('/');
        //Login start
        cy.get('#email',{timeout:10000}).clear().type('teszt@teszt.hu');
        cy.get('#password',{timeout:10000}).clear().type('TEszt1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Login ');
        cy.get('#submitButton',{timeout:10000}).click();
        //Login end

        //instances
        cy.get('#configureButton',{timeout:10000}).should('exist').click();
        cy.get('#defaultButton',{timeout:10000}).should('exist').click();
        cy.get('#nextButton',{timeout:10000}).should('exist').click();
        cy.get('#defaultValueBtn',{timeout:10000}).should('exist').click();
        cy.get('#nextButton',{timeout:10000}).contains(' Next ').should('be.visible').click({force:true});

        //nodes
        cy.get('div#defaultValueBtn',{timeout:10000}).find('button').should('exist').click({multiple:true});
        cy.get('mat-select').eq(0).click();
        cy.get('#mat-option-1').click();
        cy.get('mat-select').eq(1).click();
        cy.get('#mat-option-3').click();
        cy.get('.configure-apps').eq(0).find('button').click({force:true})
        cy.get('#selectInstance').click();
        cy.get('mat-option').click();
        cy.get('#defaultValueBtnOnApplicationCard').click({force:true});
        cy.get('.okButton').eq(0).click()

        cy.get('.configure-apps').eq(1).find('button').click({force:true})
        cy.get('#selectInstance').click();
        cy.get('mat-option').click();
        cy.get('#defaultValueBtnOnApplicationCard').click({force:true});
        cy.get('.okButton').eq(0).click()
        cy.wait(1000);
        cy.get('button',{timeout:10000}).contains(' Next ').should('exist').click({force:true});
        

        //Devices
        cy.get('mat-sidenav-content').scrollTo('bottom');
        cy.get('button',{timeout:10000}).contains(' Default ').should('exist').click({force:true});
        cy.get('#nextButton',{timeout:10000}).should('exist').click({force:true});

        //map
        cy.get('mat-sidenav-content').scrollTo('bottom');
        cy.get('#configureButton').click();
    })   
})