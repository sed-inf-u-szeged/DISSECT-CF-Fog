/**
 * Előfeltétel: Az oldal megnyílik és a regisztrációs tesztesetek sikeresen lefutottak
 * Leírás: A tesztesetek a bejelentkezés sikerességét ellenőrzi, hogy helyes adatok esetén megjelenik-e a főoldal, helytelen adatok esetén pedig hibát dob
 * L01 - Helyes adatok esetén történő bejelentkezés majd kijelentkezés
 */
describe('Logout test suit',()=>{

    it('U01- Login and Logout with good data test case',()=>{
        cy.visit('/');
        cy.get('#email',{timeout:10000}).clear().type('teszt@teszt.hu');
        cy.get('#password',{timeout:10000}).clear().type('TEszt1234');
        cy.get('mat-select',{timeout:10000}).should('have.text', 'West Herts College');
        cy.get('#submitButton',{timeout:10000}).should('have.text',' Login ');
        cy.get('#submitButton',{timeout:10000}).click();
        cy.get('.nav-list',{timeout:10000}).should('exist');
        cy.contains('Logout').click();
    })

})