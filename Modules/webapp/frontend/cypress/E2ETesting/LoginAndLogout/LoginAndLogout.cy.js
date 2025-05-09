/**
 * Előfeltétel: Az oldal megnyílik és a regisztrációs tesztesetek sikeresen lefutottak
 * Leírás: A tesztesetek a bejelentkezés sikerességét ellenőrzi, hogy helyes adatok esetén megjelenik-e a főoldal, helytelen adatok esetén pedig hibát dob
 * L01 - Helyes adatok esetén történő bejelentkezés
 * L02 - Helytelen e-mail címmel történő bejelentkezés
 * L03 - Helytelen jelszóval történő bejelntkezés
 */
describe('Login and Logout test suit',()=>{

    it('L01- Login and Logout with good data test case',()=>{
        cy.visit('/');
        cy.get('#email',{timeout:10000}).clear().type('teszt@teszt.hu');
        cy.get('#password',{timeout:10000}).clear().type('TEszt1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Login ');
        cy.get('#submitButton',{timeout:10000}).click();
        cy.get('.nav-list',{timeout:10000}).should('exist');
        cy.contains('Logout').click();
    })
    it('L02- Try to Login with wrong e-mail data test case',()=>{
        cy.visit('/');
        cy.get('#email',{timeout:10000}).clear().type('as@asdf.hu');
        cy.get('#password',{timeout:10000}).clear().type('ASdf1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Login ');
        cy.get('#submitButton',{timeout:10000}).click();
        cy.get('#dataFailedText').should('be.visible');
   
    })
    it('L03- Try to Login with wrong password data test case',()=>{
        cy.visit('/');
        cy.get('#email',{timeout:10000}).clear().type('asdf@asdf.hu');
        cy.get('#password',{timeout:10000}).clear().type('df1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Login ');
        cy.get('#submitButton',{timeout:10000}).click();
        cy.get('#dataFailedText').should('be.visible');
    })
})